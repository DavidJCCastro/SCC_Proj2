package utils;

import java.time.Duration;
import java.util.logging.Logger;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisCache {
	private static final Logger Log = Logger.getLogger(RedisCache.class.getName());
	private static final String REDIS_HOSTNAME;
	private static final int REDIS_PORT;
	public static final String CACHE_STATUS;
	private static final int REDIS_TIMEOUT = 3000;
	private static final boolean Redis_USE_TLS = false;
	private static JedisPool instance;

	static {
		REDIS_HOSTNAME = System.getenv("REDIS_HOSTNAME");
		CACHE_STATUS = System.getenv("CACHE_STATUS");

		if (REDIS_HOSTNAME == null || REDIS_HOSTNAME.isEmpty()) {
			throw new IllegalArgumentException("REDIS_HOSTNAME environment variable is not set or is empty.");
		}

		if (CACHE_STATUS == null || CACHE_STATUS.isEmpty()) {
			throw new IllegalArgumentException("CACHE_STATUS environment variable is not set or is empty.");
		}

		try {
			String redisPortEnv = System.getenv("REDIS_PORT");
			if (redisPortEnv == null || redisPortEnv.isEmpty()) {
				throw new IllegalArgumentException("REDIS_PORT environment variable is not set or is empty.");
			}
			REDIS_PORT = Integer.parseInt(redisPortEnv.replace(",", "").trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid REDIS_PORT environment variable. Must be a valid integer.", e);
		}
	}

	public synchronized static JedisPool getCachePool() {
		Log.info("Initializing Redis connection pool... \n");
		Log.info(() -> String.format("REDIS_HOSTNAME: %s \n", REDIS_HOSTNAME));
		Log.info(() -> String.format("REDIS_PORT: %d \n", REDIS_PORT));
		Log.info(() -> String.format("CACHE_STATUS: %s \n", CACHE_STATUS));
		Log.info(() -> String.format("REDIS_TIMEOUT: %d \n", REDIS_TIMEOUT));
		Log.info(() -> String.format("Redis_USE_TLS: %b \n", Redis_USE_TLS));

		if (instance != null) {
			Log.info("Redis connection pool already initialized. \n");
			return instance;
		}

		try {
			var poolConfig = new JedisPoolConfig();
			poolConfig.setMaxTotal(128); // Max total connections
			poolConfig.setMaxIdle(64); // Max idle connections
			poolConfig.setMinIdle(16); // Min idle connections
			poolConfig.setTestOnBorrow(true); // Validate connection before borrowing
			poolConfig.setTestOnReturn(true); // Validate connection after returning
			poolConfig.setTestWhileIdle(true); // Validate idle connections
			poolConfig.setNumTestsPerEvictionRun(3); // Number of tests per eviction run
			poolConfig.setBlockWhenExhausted(true); // Wait for connection if exhausted

			// Increase wait time for connections
			poolConfig.setMaxWait(Duration.ofMillis(20000));
			
			Log.info("Creating new Redis connection pool... \n");
			instance = new JedisPool(poolConfig, REDIS_HOSTNAME, REDIS_PORT, REDIS_TIMEOUT, Redis_USE_TLS);
			Log.info("Redis connection pool created successfully. \n");
		} catch (Exception e) {
			Log.severe("Error initializing Redis connection pool: " + e.getMessage() + "\n");
			throw e;
		}

		return instance;
	}

	public static boolean isEnabled() {
		return CACHE_STATUS.equalsIgnoreCase("ON");
	}
}
