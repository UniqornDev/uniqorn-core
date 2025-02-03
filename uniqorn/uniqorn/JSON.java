package uniqorn;

import aeonics.data.Data;

public class JSON
{
	public static Data object() { return Data.map(); }
	public static Data array() { return Data.list(); }
	public static Data parse(String value) { return aeonics.util.Json.decode(value); }
	public static String stringify(Object value) { return Data.of(value).toString(); }
}
