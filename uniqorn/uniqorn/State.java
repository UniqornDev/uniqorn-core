package uniqorn;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import aeonics.entity.security.User;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.manager.Timeout;
import aeonics.manager.Timeout.Tracker;
import aeonics.util.Tuples.Tuple;

public class State
{
	static ThreadLocal<String> api = ThreadLocal.withInitial(() -> null);
	private static ConcurrentHashMap<String, Tuple<Object, Long>> store = new ConcurrentHashMap<>();
	
	static
	{
		Manager.of(Timeout.class).watch(new Tracker<Void>(null)
		{
			private final int max = 60_000; // 1min
			public long delay()
			{
				long now = System.currentTimeMillis();
				long next = max;
				Iterator<Map.Entry<String, Tuple<Object, Long>>> i = store.entrySet().iterator();
				while( i.hasNext() )
				{
					Map.Entry<String, Tuple<Object, Long>> entry = i.next(); 
					Tuple<Object, Long> e = entry.getValue();
					if( e.b <= (now - max) )
					{
						Manager.of(Logger.class).finest(State.class, "Expired state entry: {}", entry.getKey());
						i.remove();
					}
					else
						next = Math.min(next, e.b + max - now + 1);
				}
				
				return Math.max(next, 1);
			}
		});
	}
	
	@SuppressWarnings("unchecked")
	private static <T> T get(String key)
	{
		Tuple<Object, Long> value = store.get(key);
		if( value == null || value.b < System.currentTimeMillis() ) return null;
		else return (T) value.a;
	}
	
	public static <T> T local(String key) { return local(key, (User.Type) null); }
	public static <T> T local(String key, User.Type user)
	{
		if( key == null ) key = "";
		if( user != null ) key = user.id() + ":" + key;
		return get(api.get() + ":" + key);
	}
	
	public static <T> T global(String key) { return global(key, (User.Type) null); }
	public static <T> T global(String key, User.Type user)
	{
		if( key == null ) key = "";
		if( user != null ) key = user.id() + ":" + key;
		return get(key);
	}
	
	@SuppressWarnings("unchecked")
	private static <T> T set(String key, Object value, long ttl)
	{
		long until = ttl > 0 ? System.currentTimeMillis() + ttl : Long.MAX_VALUE;
		Tuple<Object, Long> previous = store.put(key, Tuple.of(value, until));
		if( previous == null || previous.b < System.currentTimeMillis() ) return null;
		else return (T) previous.a;
	}
	
	public static <T> T local(String key, Object value) { return local(key, null, value, -1); }
	public static <T> T local(String key, Object value, long ttl) { return local(key, null, value, ttl); }
	public static <T> T local(String key, User.Type user, Object value) { return local(key, user, value, -1); }
	public static <T> T local(String key, User.Type user, Object value, long ttl)
	{
		if( key == null ) key = "";
		if( user != null ) key = user.id() + ":" + key;
		return set(api.get() + ":" + key, value, ttl);
	}
	
	public static <T> T global(String key, Object value) { return global(key, null, value, -1); }
	public static <T> T global(String key, Object value, long ttl) { return global(key, null, value, ttl); }
	public static <T> T global(String key, User.Type user, Object value) { return global(key, user, value, -1); }
	public static <T> T global(String key, User.Type user, Object value, long ttl)
	{
		if( key == null ) key = "";
		if( user != null ) key = user.id() + ":" + key;
		return set(key, value, ttl);
	}
}
