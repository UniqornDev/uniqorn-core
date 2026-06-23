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

/**
 * Holds transient in-memory key/value state for endpoints.
 * <p>
 * {@link #local(String) Local} state is scoped to a single endpoint; {@link #global(String) global}
 * state is shared by every endpoint on the instance. Either kind can be bound to a specific
 * {@link User} and given a time-to-live after which it expires. All state lives in memory only and
 * is lost when the instance restarts.
 * <p>
 * Stored values are returned auto-cast to the type the caller expects; reading a value as a type it
 * was not stored as throws a {@link ClassCastException}.
 */
public class State
{
	static ThreadLocal<String> api = ThreadLocal.withInitial(() -> null);
	// the user currently being served on this thread
	static ThreadLocal<User.Type> user = ThreadLocal.withInitial(() -> null);
	private static ConcurrentHashMap<String, Tuple<Object, Long>> store = new ConcurrentHashMap<>();
	
	static
	{
		Manager.of(Timeout.class).watch(new Tracker<Void>("Uniqorn State Timeout Tracker")
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
	
	/**
	 * Retrieves a value from the calling endpoint's local state.
	 * @param <T> the expected value type
	 * @param key the state entry name
	 * @return the stored value, or {@code null} if it is absent or has expired
	 */
	public static <T> T local(String key) { return local(key, (User.Type) null); }

	/**
	 * Retrieves a user-bound value from the calling endpoint's local state.
	 * @param <T> the expected value type
	 * @param key the state entry name
	 * @param user the user the value was stored for, or {@code null} for the entry bound to no user
	 * @return the stored value, or {@code null} if it is absent or has expired
	 */
	public static <T> T local(String key, User.Type user)
	{
		if( key == null ) key = "";
		if( user != null ) key = user.id() + ":" + key;
		return get(api.get() + ":" + key);
	}
	
	/**
	 * Retrieves a value from the global state shared by all endpoints.
	 * @param <T> the expected value type
	 * @param key the state entry name
	 * @return the stored value, or {@code null} if it is absent or has expired
	 */
	public static <T> T global(String key) { return global(key, (User.Type) null); }

	/**
	 * Retrieves a user-bound value from the global state shared by all endpoints.
	 * @param <T> the expected value type
	 * @param key the state entry name
	 * @param user the user the value was stored for, or {@code null} for the entry bound to no user
	 * @return the stored value, or {@code null} if it is absent or has expired
	 */
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
	
	/**
	 * Stores a value in the calling endpoint's local state.
	 * @param <T> the previous value type
	 * @param key the state entry name
	 * @param value the value to store
	 * @return the previous value, or {@code null} if there was none
	 */
	public static <T> T local(String key, Object value) { return local(key, null, value, -1); }

	/**
	 * Stores a value in the calling endpoint's local state with an expiration.
	 * @param <T> the previous value type
	 * @param key the state entry name
	 * @param value the value to store
	 * @param ttl the time-to-live in milliseconds after which the value expires, or a non-positive value to never expire
	 * @return the previous value, or {@code null} if there was none
	 */
	public static <T> T local(String key, Object value, long ttl) { return local(key, null, value, ttl); }

	/**
	 * Stores a user-bound value in the calling endpoint's local state.
	 * @param <T> the previous value type
	 * @param key the state entry name
	 * @param user the user to bind the value to, or {@code null} to bind it to no user
	 * @param value the value to store
	 * @return the previous value, or {@code null} if there was none
	 */
	public static <T> T local(String key, User.Type user, Object value) { return local(key, user, value, -1); }

	/**
	 * Stores a user-bound value in the calling endpoint's local state with an expiration.
	 * @param <T> the previous value type
	 * @param key the state entry name
	 * @param user the user to bind the value to, or {@code null} to bind it to no user
	 * @param value the value to store
	 * @param ttl the time-to-live in milliseconds after which the value expires, or a non-positive value to never expire
	 * @return the previous value, or {@code null} if there was none
	 */
	public static <T> T local(String key, User.Type user, Object value, long ttl)
	{
		if( key == null ) key = "";
		if( user != null ) key = user.id() + ":" + key;
		return set(api.get() + ":" + key, value, ttl);
	}
	
	/**
	 * Stores a value in the global state shared by all endpoints.
	 * @param <T> the previous value type
	 * @param key the state entry name
	 * @param value the value to store
	 * @return the previous value, or {@code null} if there was none
	 */
	public static <T> T global(String key, Object value) { return global(key, null, value, -1); }

	/**
	 * Stores a value in the global state shared by all endpoints with an expiration.
	 * @param <T> the previous value type
	 * @param key the state entry name
	 * @param value the value to store
	 * @param ttl the time-to-live in milliseconds after which the value expires, or a non-positive value to never expire
	 * @return the previous value, or {@code null} if there was none
	 */
	public static <T> T global(String key, Object value, long ttl) { return global(key, null, value, ttl); }

	/**
	 * Stores a user-bound value in the global state shared by all endpoints.
	 * @param <T> the previous value type
	 * @param key the state entry name
	 * @param user the user to bind the value to, or {@code null} to bind it to no user
	 * @param value the value to store
	 * @return the previous value, or {@code null} if there was none
	 */
	public static <T> T global(String key, User.Type user, Object value) { return global(key, user, value, -1); }

	/**
	 * Stores a user-bound value in the global state shared by all endpoints with an expiration.
	 * @param <T> the previous value type
	 * @param key the state entry name
	 * @param user the user to bind the value to, or {@code null} to bind it to no user
	 * @param value the value to store
	 * @param ttl the time-to-live in milliseconds after which the value expires, or a non-positive value to never expire
	 * @return the previous value, or {@code null} if there was none
	 */
	public static <T> T global(String key, User.Type user, Object value, long ttl)
	{
		if( key == null ) key = "";
		if( user != null ) key = user.id() + ":" + key;
		return set(key, value, ttl);
	}
}
