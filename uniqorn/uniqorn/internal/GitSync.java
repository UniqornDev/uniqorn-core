package uniqorn.internal;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import aeonics.data.Data;
import aeonics.entity.Registry;
import aeonics.git.Bare;
import aeonics.git.GitRepo;
import aeonics.http.HttpException;
import aeonics.manager.Config;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.template.Factory;
import aeonics.util.StringUtils;
import aeonics.util.Tuples.Single;
import aeonics.util.Tuples.Triple;
import uniqorn.Api;
import uniqorn.Endpoint;
import uniqorn.Workspace;

/**
 * Synchronize files and endpoints
 */
public class GitSync
{
	public static final String src = "/src";
	public static final String www = "/www";
	public static final Path srcPath = Paths.get(src);
	public static final Path wwwPath = Paths.get(www);
	public static volatile aeonics.entity.Storage.Type appStorage;
	
	/**
	 * Applies the provided changes effectively by publishing, updating or removing uniqorn endpoints.
	 * 
	 * @param changes the list of changes to apply (path, previous content, new content). 
	 * 	It may contain non-applicable file paths. 
	 * 	If a file was removed, the new content is null.
	 * 	If a file was created, the old content is null.
	 * @return the complete status message to send to the client
	 */
	public static String apply(Set<Triple<String, byte[], byte[]>> changes)
	{
		long start = System.currentTimeMillis();
		StringBuilder out =   new StringBuilder();
		StringBuilder human = new StringBuilder("---------------\n");
		
		int created = 0;
		int updated = 0;
		int deleted = 0;
		int error = 0;
		Single<Integer> ignored = Single.of(0);
		
		// handle www static asset files
		Single<Integer> wwwError = Single.of(0);
		changes.removeIf(change ->
		{
			if( !change.a.startsWith("/www/") ) return false;
			if( change.a.endsWith("/") ) return true; // skip directories

			// path traversal guard: ensure resolved path stays under /www/
			Path resolved = wwwPath.relativize(Paths.get(change.a));
			if( resolved.startsWith("..") )
			{
				out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "ignored").put("error", null).put("info", "Path traversal rejected") + "\n");
				ignored.a++;
				return true;
			}
			String relativePath = resolved.toString().replace('\\', '/');
			try
			{
				if( change.c == null )
				{
					// file removed
					if( appStorage != null ) appStorage.remove(relativePath);
					out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "removed").put("error", null).put("info", null) + "\n");
					human.append(change.a + " - removed\n");
				}
				else
				{
					// file added or modified
					if( appStorage != null ) appStorage.put(relativePath, change.c);
					out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "extracted").put("error", null).put("info", null) + "\n");
					human.append(change.a + " - extracted\n");
				}
			}
			catch(Exception e)
			{
				out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "error").put("error", e.getMessage()).put("info", null) + "\n");
				human.append(change.a + " - " + Objects.requireNonNullElse(e.getMessage(), "null").replaceAll("\n", " ") + "\n");
				wwwError.a++;
			}
			return true; // remove from further processing
		});

		changes.removeIf(change ->
		{
			// file path must be "/src/[workspace name]/[file name].java"
			if( !change.a.startsWith("/src/") )
			{
				out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "ignored").put("error", null).put("info", "Not in 'src' folder") + "\n");
				ignored.a++;
				return true;
			}
			if( !change.a.endsWith(".java") )
			{
				out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "ignored").put("error", null).put("info", "Not a '.java' file") + "\n");
				ignored.a++;
				return true;
			}
			Path resolved = srcPath.relativize(Paths.get(change.a));
			if( resolved.getNameCount() != 2 )
			{
				out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "ignored").put("error", null).put("info", "Not in workspace subfolder") + "\n");
				ignored.a++;
				return true;
			}
			if( !StringUtils.isComposedOf(resolved.getName(0).toString(), "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.") )
			{
				out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "ignored").put("error", null).put("info", "Invalid workspace name. Only alphanumeric and '-_.' is allowed") + "\n");
				human.append(change.a + " - Invalid workspace name. Only alphanumeric and '-_.' is allowed\n");
				ignored.a++;
				return true;
			}
			return false;
		});
		
		// first do the delete and update to keep track of the quotas
		for( Triple<String, byte[], byte[]> change : changes )
		{
			if( change.b == null ) continue; // file created
			
			Endpoint.Type endpoint = Registry.of(Endpoint.class).get((e) -> 
			{ 
				return e.valueOf("path").asString().equals(change.a);
			});
			
			Path resolved = srcPath.relativize(Paths.get(change.a));
			Workspace.Type workspace = Registry.of(Workspace.class).get(resolved.getName(0).toString());
			
			String oldUrl = null;
			if( endpoint != null )
			{
				Api api = endpoint.api();
				if( api != null )
				{
					aeonics.http.Endpoint.Type real = api.api();
					if( real != null )
					{
						oldUrl = real.method() + " " + 
							Manager.of(Config.class).get(Api.class, "prefix").asString() + 
							(workspace != null ? workspace.valueOf("prefix").asString() : "") + 
							real.url();
					}
				}
			}
			
			if( change.c == null ) // file removed
			{
				if( endpoint == null )
				{
					out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "ignored").put("error", null).put("info", "Nothing to undeploy") + "\n");
					ignored.a++;
				}
				else
				{
					endpoint.internal(false);
					Registry.of(Endpoint.class).remove(endpoint);
					out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", oldUrl).put("status", "removed").put("error", null).put("info", null) + "\n");
					deleted++;
					if( workspace != null ) workspace.removeRelation("endpoints", endpoint);
				}
			}
			else // file updated
			{
				if( endpoint == null ) change.b = null; // mark as a create
				else
				{
					try
					{
						endpoint.updateHead();
						String newUrl = endpoint.api().api().method() + " " + 
							Manager.of(Config.class).get(Api.class, "prefix").asString() + 
							(workspace != null ? workspace.valueOf("prefix").asString() : "") + 
							endpoint.api().api().url();
						
						out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", newUrl).put("status", "updated").put("error", null).put("info", null) + "\n");
						updated++;
					}
					catch(HttpException he)
					{
						if( he.code == 422 && he.data != null && he.data.isMap() && he.data.isMap("error") && !he.data.get("error").isEmpty("message") )
						{
							out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "error").put("error", he.data.get("error").asString("message")).put("info", null) + "\n");
							human.append(change.a + " - " + he.data.get("error").asString("message").replaceAll("\n", " ") + "\n");
							error++;
						}
						else
						{
							out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "error").put("error", he.data.asString()).put("info", null) + "\n");
							human.append(change.a + " - " + he.data.asString().replaceAll("\n", " ") + "\n");
							error++;
						}
					}
					catch(Exception e)
					{
						out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", oldUrl).put("status", "error").put("error", e.getMessage()).put("info", "Previous version preserved") + "\n");
						human.append(change.a + " - " + Objects.requireNonNullElse(e.getMessage(), "null").replaceAll("\n", " ") + "\n");
						error++;
					}
				}
			}
		}
		
		// then process the new files
		for( Triple<String, byte[], byte[]> change : changes )
		{
			if( change.b != null ) continue; // file created
			
			// create workspace if needed
			Path resolved = srcPath.relativize(Paths.get(change.a));
			Workspace.Type workspace = Registry.of(Workspace.class).get(resolved.getName(0).toString());
			if( workspace == null )
			{
				synchronized(Workspace.class)
				{
					if( Registry.of(Workspace.class).size() >= Manager.of(Config.class).get(Api.class, "workspaces").asInt() )
					{
						out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "error").put("error", "Maximum number of workspaces reached").put("info", "Could not create workspace " + resolved.getName(0).toString()) + "\n");
						human.append(change.a + " - Maximum number of workspaces reached\n");
						error++;
						continue;
					}
					
					workspace = Factory.of(Workspace.class).get(Workspace.class).create()
						.name(resolved.getName(0).toString())
						.parameter("prefix", "/" + resolved.getName(0).toString());
				}
			}
			
			synchronized(Endpoint.class)
			{
				try
				{
					if( Registry.of(Endpoint.class).size() >= Manager.of(Config.class).get(Api.class, "endpoints").asInt() )
						throw new Exception("Maximum number of endpoints reached");
					
					Endpoint.Type endpoint = Factory.of(Endpoint.class).get(Endpoint.class).create(Data.map().put("parameters", Data.map()
						.put("enabled", true)
						.put("path", change.a)
						.put("sha", "0")
						))
						.name("Git endpoint " + change.a)
						.internal(true)
						.<Endpoint.Type>cast();
					
					workspace.addRelation("endpoints", endpoint);
					endpoint.updateHead();
					String newUrl = endpoint.api().api().method() + " " + 
						Manager.of(Config.class).get(Api.class, "prefix").asString() + 
						(workspace != null ? workspace.valueOf("prefix").asString() : "") + 
						endpoint.api().api().url();
					
					out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", newUrl).put("status", "created").put("error", null).put("info", null) + "\n");
					created++;
				}
				catch(HttpException he)
				{
					if( he.code == 422 && he.data != null && he.data.isMap() && he.data.isMap("error") && !he.data.get("error").isEmpty("message") )
					{
						out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "error").put("error", he.data.get("error").asString("message")).put("info", null) + "\n");
						human.append(change.a + " - " + he.data.get("error").asString("message").replaceAll("\n", " ") + "\n");
						error++;
					}
					else
					{
						out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "error").put("error", he.data.asString()).put("info", null) + "\n");
						human.append(change.a + " - " + he.data.asString().replaceAll("\n", " ") + "\n");
						error++;
					}
				}
				catch(Exception e)
				{
					out.append("[@Uniqorn] " + Data.map().put("file", change.a).put("uri", null).put("status", "error").put("error", e.getMessage()).put("info", null) + "\n");
					human.append(change.a + " - " + Objects.requireNonNullElse(e.getMessage(), "null").replaceAll("\n", " ") + "\n");
					error++;
				}
			}
		}
		
		long end = System.currentTimeMillis();
		human.append("Done: created=" + created + " updated=" + updated + " removed=" + deleted + " ignored=" + ignored.a + " error=" + (error + wwwError.a) + " in " + (end-start) + "ms\n");
		human.append("---------------\n");
		
		return out.append(human).toString();
	}
	
	/**
	 * Scans the git repository and checks that all workspaces and endpoints are created.
	 * Removing and adding as necessary.
	 */
	public static void resync()
	{
		GitRepo.Type repo = Registry.of(GitRepo.class).get("uniqorn");
		List<String> all = Bare.list(repo.store(), repo.root(), null);
		
		// first: remove endpoints
		for( Endpoint.Type endpoint : Registry.of(Endpoint.class).filter(x -> true) )
		{
			if( !all.contains(endpoint.valueOf("path").asString()) )
				Registry.of(Endpoint.class).remove(endpoint);
		}
		
		// second: remove workspaces
		for( Workspace.Type workspace : Registry.of(Workspace.class).filter(x -> true) )
		{
			if( !all.contains(src + "/" + workspace.name() + "/") )
				Registry.of(Workspace.class).remove(workspace);
		}
		
		// third: create workspaces
		for( String path : all )
		{
			if( !path.startsWith(src + "/") || !path.endsWith("/") ) continue;
			if( path.indexOf('/', 1) != path.lastIndexOf('/', path.length()-2) ) continue;
			
			String name = path.substring(path.indexOf('/', 1)+1, path.length()-1);
			Workspace.Type workspace = Registry.of(Workspace.class).get(name);
			if( workspace == null )
			{
				synchronized(Workspace.class)
				{
					if( !StringUtils.isComposedOf(name, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.") )
						break;
					
					if( Registry.of(Workspace.class).size() >= Manager.of(Config.class).get(Api.class, "workspaces").asInt() )
						break;
					
					workspace = Factory.of(Workspace.class).get(Workspace.class).create()
						.name(name)
						.parameter("prefix", "/" + name);
				}
			}
		}
		
		// fourth: create endpoints
		for( String path : all )
		{
			if( !path.startsWith(src + "/") || !path.endsWith(".java") ) continue;
			Path p = Paths.get(path);
			if( p.getNameCount() != 3 ) continue;
			if( Registry.of(Endpoint.class).get(e -> e.valueOf("path").asString().equals(path)) != null ) continue;
			
			Workspace.Type workspace = Registry.of(Workspace.class).get(p.getName(1).toString());
			if( workspace == null ) continue; // the workspace limit was reached
			
			synchronized(Endpoint.class)
			{
				if( Registry.of(Endpoint.class).size() >= Manager.of(Config.class).get(Api.class, "endpoints").asInt() )
					break;
				
				try
				{
					Endpoint.Type endpoint = Factory.of(Endpoint.class).get(Endpoint.class).create(Data.map().put("parameters", Data.map()
						.put("enabled", true)
						.put("path", path)
						.put("sha", "0")
						))
						.name("Git endpoint " + path)
						.internal(true)
						.<Endpoint.Type>cast();
					
					workspace.addRelation("endpoints", endpoint);
					endpoint.updateHead();
				}
				catch(Exception e)
				{
					Manager.of(Logger.class).warning(Endpoint.class, "Recompile of {} failed with {}", path, e);
				}
			}
		}
	}
}
