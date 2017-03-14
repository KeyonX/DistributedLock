package redislock;

import redis.clients.jedis.JedisPool;

/**
 * Created by KeyonX on 2016/12/20.
 */
public class RedisLockFactory {
    protected static JedisPool pool;
    public static void init(JedisPool jedisPool){
        if (pool == null) {
            synchronized (RedisLockFactory.class) {
                if (pool == null) {
                    pool = jedisPool;
                }
            }
        }
    }
    //调用方保证lockKey的全局唯一性，防止不同的业务场景使用同一个锁
    public static RedisLock getLock(String lockKey, int seconds){
        return new DistributedLock(lockKey,seconds);
    }
}
