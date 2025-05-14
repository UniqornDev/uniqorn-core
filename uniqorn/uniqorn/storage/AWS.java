package uniqorn.storage;

import aeonics.data.Data;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import aeonics.entity.Storage;
import aeonics.template.Parameter;
import aeonics.template.Template;
import aeonics.util.Http;

public class AWS extends Storage
{
	/**
	 * AWS S3 (v4) protocol combines REST-like semantics with XML-based responses.
	 * It relies on canonical request signing, which imposes strict ordering and encoding rules
	 * that deviate from conventional HTTP behavior.
	 *
	 * This implementation uses minimal manual request construction and XML parsing
	 * to avoid the complexity and overhead of full AWS SDKs.
	 *
	 * Security considerations:
	 * - Assumes AWS is a trusted endpoint; returned XML is considered well-formed and benign.
	 * - Object keys (paths) may contain any character, including path traversal patterns or markup-like strings.
	 *   These are valid in S3 but must be carefully handled when rendering in UI or passing to file systems.
	 *
	 * Valid path includes: "/foo/bar/.././//&lt;script&gt;hack();"
	 */
	public static class Type extends Storage.Type
	{
		private static String escapeXml(String s)
		{
			return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&apos;");
		}
		
		private static String unescapeXml(String s)
		{
			return s.replace("&amp;", "&")
				.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&quot;", "\"")
				.replace("&apos;", "'");
		}
		
		private static List<String> parseKeys(String xml)
		{
			List<String> keys = new ArrayList<>();
			int index = 0;
			while ((index = xml.indexOf("<Key>", index)) != -1)
			{
				int start = index + 5;
				int end = xml.indexOf("</Key>", start);
				if (end == -1) break;
				String escaped = xml.substring(start, end);
				keys.add(unescapeXml(escaped));
				index = end + 6;
			}
			return keys;
		}
		
		private static List<String> parseCommonPrefixes(String xml)
		{
			List<String> result = new ArrayList<>();
			int index = 0;
			while ((index = xml.indexOf("<CommonPrefixes>", index)) != -1)
			{
				int start = xml.indexOf("<Prefix>", index);
				int end = xml.indexOf("</Prefix>", start);
				if (start == -1 || end == -1) break;
				String value = xml.substring(start + 8, end); // 8 = length of "<Prefix>"
				result.add(value);
				index = end + 9; // move past "</Prefix>"
			}
			return result;
		}
		
		private static String buildQueryString(Data query)
		{
			List<String> pairs = new ArrayList<>();
			for (Map.Entry<String, Data> e : query.entrySet())
			{
				String encodedKey = urlEncode(e.getKey());
				String value = e.getValue().asString();
				String encodedValue = value == null ? "" : urlEncode(value);
				pairs.add(encodedKey + "=" + encodedValue);
			}
			Collections.sort(pairs); // ASCII sort
			return String.join("&", pairs);
		}
		
		private static String extractContinuationToken(String xml)
		{
			int startTag = xml.indexOf("<NextContinuationToken>");
			if (startTag == -1) return null;
			int start = startTag + "<NextContinuationToken>".length();
			int end = xml.indexOf("</NextContinuationToken>", start);
			if (end == -1) return null;
			return unescapeXml(xml.substring(start, end));
		}
		
		private static String getAmzDate()
		{
			ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
			DateTimeFormatter amzFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
			return now.format(amzFormatter);
		}

		private static String getDateStamp()
		{
			ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
			DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
			return now.format(dateFormatter);
		}
		
		private static String urlEncode(String value)
		{
			StringBuilder encoded = new StringBuilder();
			for (char c : value.toCharArray())
			{
				if ((c >= 'A' && c <= 'Z') ||
					(c >= 'a' && c <= 'z') ||
					(c >= '0' && c <= '9') ||
					c == '-' || c == '_' || c == '.' || c == '~')
				{
					encoded.append(c);
				}
				else
				{
					encoded.append('%');
					encoded.append(String.format("%02X", (int) c));
				}
			}
			return encoded.toString();
		}
		
		private static String encodePath(String path)
		{
			try
			{
				// AWS requires each path segment to be URI-encoded individually
				String[] segments = path.split("/");
				StringBuilder encoded = new StringBuilder();
				for (int i = 0; i < segments.length; i++)
				{
					if (i > 0) encoded.append("/");
					encoded.append(URLEncoder.encode(segments[i], "UTF-8")
						.replace("+", "%20")
						.replace("*", "%2A")
						.replace("%7E", "~"));
				}
				
				// Handle trailing slash explicitly
				if (path.endsWith("/") && !encoded.toString().endsWith("/"))
				{
					encoded.append("/");
				}
				return encoded.toString();
			}
			catch (UnsupportedEncodingException e)
			{
				throw new RuntimeException("UTF-8 not supported", e);
			}
		}
		
		private static String canonicalHeadersString(Data headers, String signedHeaders)
		{
			String[] keys = signedHeaders.split(";");
			Arrays.sort(keys);

			StringBuilder sb = new StringBuilder();
			for( String key : keys )
			{
				String value = headers.get(key).asString().trim().replaceAll("\\s+", " ");
				sb.append(key.toLowerCase()).append(":").append(value).append("\n");
			}
			return sb.toString();
		}
		
		private static String hash(String input)
		{
			return hash(input.getBytes(StandardCharsets.UTF_8));
		}
		
		private static String hash(byte[] input)
		{
			try
			{
				MessageDigest digest = MessageDigest.getInstance("SHA-256");
				byte[] hashed = digest.digest(input);
				return bytesToHex(hashed);
			}
			catch (Exception e)
			{
				throw new RuntimeException("Unable to compute SHA-256 hash", e);
			}
		}

		private static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName)
		{
			byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
			byte[] kDate = hmac(kSecret, dateStamp);
			byte[] kRegion = hmac(kDate, regionName);
			byte[] kService = hmac(kRegion, serviceName);
			return hmac(kService, "aws4_request");
		}

		private static byte[] hmac(byte[] key, String data)
		{
			try
			{
				Mac mac = Mac.getInstance("HmacSHA256");
				mac.init(new SecretKeySpec(key, "HmacSHA256"));
				return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			} catch (Exception e)
			{
				throw new RuntimeException("HMAC error", e);
			}
		}

		private static String bytesToHex(byte[] bytes)
		{
			char[] hexArray = "0123456789abcdef".toCharArray();
			char[] hexChars = new char[bytes.length * 2];
			for (int j = 0; j < bytes.length; j++)
			{
				int v = bytes[j] & 0xFF;
				hexChars[j * 2]	 = hexArray[v >>> 4];
				hexChars[j * 2 + 1] = hexArray[v & 0x0F];
			}
			return new String(hexChars);
		}
		
		private String host()
		{
			return valueOf("bucket").asString() + ".s3." + valueOf("region").asString() + ".amazonaws.com";
		}
		
		private Data signedHeaders(String method, String canonicalUri, String canonicalQueryString, String payloadHash)
		{
			String service = "s3";
			String host = host();
			String amzDate = getAmzDate();
			String dateStamp = getDateStamp();

			Data headers = Data.map()
				.put("host", host)
				.put("x-amz-content-sha256", payloadHash)
				.put("x-amz-date", amzDate);
			
			String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
			String canonicalHeaders = canonicalHeadersString(headers, signedHeaders);
			String canonicalRequest = method + "\n" +
									canonicalUri + "\n" +
									canonicalQueryString + "\n" +
									canonicalHeaders + "\n" +
									signedHeaders + "\n" +
									payloadHash;

			String credentialScope = dateStamp + "/" + valueOf("region").asString() + "/" + service + "/aws4_request";
			String stringToSign = "AWS4-HMAC-SHA256\n" +
								amzDate + "\n" +
								credentialScope + "\n" +
								hash(canonicalRequest);

			byte[] signingKey = getSignatureKey(valueOf("secret").asString(), dateStamp, valueOf("region").asString(), service);
			String signature = bytesToHex(hmac(signingKey, stringToSign));

			String authorization = "AWS4-HMAC-SHA256 Credential=" + valueOf("key").asString() + "/" + credentialScope +
								", SignedHeaders=" + signedHeaders +
								", Signature=" + signature;
			headers.put("Authorization", authorization);
			
			return headers;
		}
		
		@Override
		public void put(String path, byte[] content)
		{
			if( path == null || path.endsWith("/") ) throw new IllegalArgumentException("Invalid path");
			while( path.startsWith("/") ) path = path.substring(1);
			if( path.isBlank() ) throw new IllegalArgumentException("Invalid path");
			
			String method = "PUT";
			String uri = "/" + encodePath(path);
			String payloadHash = hash(content);

			Data headers = signedHeaders(method, uri, "", payloadHash);
			headers.put("Content-Length", String.valueOf(content.length));
			headers.put("Content-Type", "application/octet-stream");

			Http.post("https://" + host() + uri, Data.of(new String(content, StandardCharsets.ISO_8859_1)), headers, method, 20000);
		}

		@Override
		public byte[] get(String path)
		{
			if( path == null ) return null;
			while( path.startsWith("/") ) path = path.substring(1);
			if( path.isBlank() ) return null;
			
			String method = "GET";
			String endpoint = "https://" + host();
			
			String canonicalUri = "/" + encodePath(path);
			String canonicalQueryString = "";
			
			Data headers = signedHeaders(method, canonicalUri, canonicalQueryString, "UNSIGNED-PAYLOAD");
			
			Data response = Http.get(endpoint + canonicalUri, null, headers, method, 10000);
			return response.asString().getBytes(StandardCharsets.ISO_8859_1);
		}

		@Override
		public boolean containsEntry(String path)
		{
			if( path == null || path.endsWith("/") ) return false;
			while( path.startsWith("/") ) path = path.substring(1);
			if( path.isBlank() ) return false;
				
			String method = "HEAD";
			String endpoint = "https://" + host();
			String canonicalUri = "/" + encodePath(path);
			String canonicalQueryString = "";

			Data headers = signedHeaders(method, canonicalUri, canonicalQueryString, "UNSIGNED-PAYLOAD");
			try
			{
				Http.get(endpoint, null, headers, method, 5000);
				return true;
			}
			catch(Http.Error he)
			{
				// S3 is ambiguous about 404 vs 403 so treat both as not found
				if( he.code == 404 || he.code == 403) return false;
				else throw he;
			}
		}

		@Override
		public boolean containsPath(String path)
		{
			if( path == null ) return false;
			while( path.startsWith("/") ) path = path.substring(1);
			if( path.isBlank() ) return true;
			
			String method = "GET";
			String prefix = path.endsWith("/") ? path : path + "/";
			
			Data queryParams = Data.map().put("prefix", prefix).put("max-keys", "1");
			String queryString = buildQueryString(queryParams);
			Data headers = signedHeaders(method, "/", queryString, "UNSIGNED-PAYLOAD");
			
			try
			{
				Data result = Http.get("https://" + host() + "/?" + queryString, null, headers, method, 5000);
				return result.asString().contains("<Key>" + prefix);
			}
			catch(Http.Error he)
			{
				// S3 is ambiguous about 404 vs 403 so treat both as not found
				if( he.code == 404 || he.code == 403) return false;
				else throw he;
			}
		}

		@Override
		public void remove(String path)
		{
			if( path == null ) return;
			while( path.startsWith("/") ) path = path.substring(1);
			
			if( !path.endsWith("/") )
			{
				String uri = "/" + encodePath(path);
				Data headers = signedHeaders("DELETE", uri, "", "UNSIGNED-PAYLOAD");
				Http.get("https://" + host() + uri, Data.map(), headers, "DELETE", 5000);
				return;
			}
			
			String continuationToken = null;

			do {
				// Build query
				Data query = Data.map()
					.put("list-type", "2")
					.put("prefix", path);
				if (continuationToken != null)
					query.put("continuation-token", continuationToken);

				String queryString = buildQueryString(query);
				Data headers = signedHeaders("GET", "/", queryString, "UNSIGNED-PAYLOAD");
				Data response = Http.get("https://" + host() + "/?" + queryString, null, headers, "GET", 10000);
				String xml = response.asString();

				List<String> keys = parseKeys(xml);
				if (keys.isEmpty()) break;

				deleteBatch(keys);

				continuationToken = extractContinuationToken(xml);
			} while (continuationToken != null);
		}
		
		private void deleteBatch(List<String> keys)
		{
			StringBuilder xml = new StringBuilder();
			xml.append("<Delete><Quiet>true</Quiet>");
			for (String key : keys)
			{
				if( key != null && !key.isBlank() )
					xml.append("<Object><Key>").append(escapeXml(key)).append("</Key></Object>");
			}
			xml.append("</Delete>");

			String body = xml.toString();
			String payloadHash = hash(body);
			Data headers = signedHeaders("POST", "/?delete", "", payloadHash);
			headers.put("Content-Type", "application/xml");

			Http.post("https://" + host() + "/?delete", Data.of(body), headers, "POST", 10000);
		}

		@Override
		public Collection<String> list(String path)
		{
			if( path == null ) return Collections.emptyList();
			while( path.startsWith("/") ) path = path.substring(1);
			
			List<String> results = new ArrayList<>();

			if( containsEntry(path) )
			{
				results.add(path);
				return results;
			}
			
			String prefix = path.isBlank() || path.endsWith("/") ? path : path + "/";
			String continuationToken = null;
			
			do {
				Data query = Data.map()
					.put("list-type", "2")
					.put("prefix", prefix);
				if (continuationToken != null) {
					query.put("continuation-token", continuationToken);
				}
				
				String queryString = buildQueryString(query);
				Data headers = signedHeaders("GET", "/", queryString, "UNSIGNED-PAYLOAD");
				Data response = Http.get("https://" + host() + "/?" + queryString, null, headers, "GET", 10000);
				String xml = response.asString();

				List<String> keys = parseKeys(xml);
				for (String key : keys)
				{
					if( !key.endsWith("/") )
						results.add(key);
				}

				continuationToken = extractContinuationToken(xml);
			} while (continuationToken != null);

			return results;
		}

		@Override
		public Collection<String> tree(String path)
		{
			if( path == null ) return Collections.emptyList();
			while( path.startsWith("/") ) path = path.substring(1);
			
			List<String> results = new ArrayList<>();

			if( containsEntry(path) ) return results;
			
			String prefix = path.isBlank() || path.endsWith("/") ? path : path + "/";
			String continuationToken = null;

			do {
				Data query = Data.map()
					.put("list-type", "2")
					.put("prefix", prefix)
					.put("delimiter", "/");
				if (continuationToken != null) {
					query.put("continuation-token", continuationToken);
				}
				
				String queryString = buildQueryString(query);
				Data headers = signedHeaders("GET", "/", queryString, "UNSIGNED-PAYLOAD");
				Data response = Http.get("https://" + host() + "/?" + queryString, null, headers, "GET", 10000);
				String xml = response.asString();

				// <CommonPrefixes><Prefix>path/dir1/</Prefix></CommonPrefixes>
				List<String> dirs = parseCommonPrefixes(xml);
				for (String dir : dirs)
				{
					String relative = dir.substring(prefix.length());
					results.add(relative);
				}

				// <Contents><Key>path/file.txt</Key></Contents>
				List<String> keys = parseKeys(xml);
				for (String key : keys)
				{
					if( !key.endsWith("/") )
					{
						String relative = key.substring(prefix.length());
						if( !relative.contains("/") )
							results.add(relative);
					}
				}

				continuationToken = extractContinuationToken(xml);
			} while (continuationToken != null);

			return results;
		}

		@Override
		public void clear()
		{
			remove("/");
		} 
	}
	
	protected Class<? extends Type> defaultTarget() { return Type.class; }
	protected Supplier<? extends Type> defaultCreator() { return Type::new; }
	
	@Override
	public Template<? extends Storage.Type> template()
	{
		return super.template()
			.summary("AWS S3 Object Storage")
			.description("This object storage provides an abstraction layer on top of AWS S3.")
			.add(new Parameter("region")
				.summary("Region")
				.description("The AWS S3 region.")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.min(3))
			.add(new Parameter("key")
				.summary("Access Key ID")
				.description("The AWS S3 access key ID.")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.min(3))
			.add(new Parameter("secret")
				.summary("Secret Access Key")
				.description("The AWS S3 secret access key.")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.min(3))
			.add(new Parameter("bucket")
				.summary("Bucket")
				.description("The AWS S3 bucket name.")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.min(3))
			;
	}
}
