package uniqorn;

import aeonics.data.Data;
import aeonics.util.Functions.Predicate;
import aeonics.util.StringUtils;

public class Input 
{
	public static final Predicate<Data> isNotEmpty = (data) ->
	{
		return data != null && !data.isEmpty();
	};
	
	public static final Predicate<Data> isEmpty = (data) ->
	{
		return data == null || data.isEmpty();
	};
	
	public static final Predicate<Data> isAlphaNumeric = (data) ->
	{
		return data != null && StringUtils.isAlphaNum(data.asString());
	};
	
	public static final Predicate<Data> isBoolean = (data) ->
	{
		return data != null && StringUtils.isBoolean(data.asString());
	};
	
	public static final Predicate<Data> isEmail = (data) ->
	{
		return data != null && StringUtils.isEmailSimple(data.asString());
	};
	
	public static final Predicate<Data> isInteger = (data) ->
	{
		return data != null && StringUtils.isInteger(data.asString());
	};
	
	public static final Predicate<Data> isFloatingPoint = (data) ->
	{
		return data != null && StringUtils.isFloatingPoint(data.asString());
	};
	
	public static final Predicate<Data> isPositive = (data) ->
	{
		return data != null && StringUtils.isFloatingPoint(data.asString()) && !data.asString().startsWith("-");
	};
	
	public static final Predicate<Data> isNagative = (data) ->
	{
		return data != null && StringUtils.isFloatingPoint(data.asString()) && data.asString().startsWith("-");
	};
	
	public static final Predicate<Data> isFile = (data) ->
	{
		return data != null && data.isMap() && data.containsKey("name") && data.containsKey("mime") && data.containsKey("content");
	};
	
	public static final Predicate<Data> hasFileExtension(final String value)
	{
		return (data) -> isFile.test(data) && data.asString("name").endsWith(value);
	}
	
	public static final Predicate<Data> hasMimeType(final String value)
	{
		return (data) -> isFile.test(data) && data.asString("mime").equals(value);
	}
	
	public static final Predicate<Data> minSize(final int value)
	{
		return (data) -> data.size() >= value;
	}
	
	public static final Predicate<Data> maxSize(final int value)
	{
		return (data) -> data.size() <= value;
	}
}
