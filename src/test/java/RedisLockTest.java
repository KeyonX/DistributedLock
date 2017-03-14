import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redislock.RedisLockFactory;
import redislock.RedisLock;

import java.util.concurrent.TimeUnit;

/**
 * Created by KeyonX on 2017/03/14.
 */
public class RedisLockTest {
    private static JedisPool jedisPool = initJedisPool("yourIp", "yourPort", "yourPassword");

    public static void main(String[] args) {
        RedisLockFactory.init(jedisPool);
        String lockKey = "test";
        for(int i=0;i<10;i++) {
            RedisLock lock = RedisLockFactory.getLock(lockKey, 100);
            try{
                if(lock.tryLock(1000, TimeUnit.MILLISECONDS,50)){
                    System.out.println("获取到锁，执行业务操作");
                }
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                lock.unLock();
            }
        }
    }

    public static JedisPool initJedisPool(String ip,String port,String psw){
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxIdle(50);
        config.setMinIdle(10);
        config.setMaxTotal(300);
        String redisHost = ip;
        String redisPort = port;
        config.setTestOnBorrow(true);
        config.setTestWhileIdle(true);
        config.setNumTestsPerEvictionRun(10);
        config.setTimeBetweenEvictionRunsMillis(60000);
        config.setMaxWaitMillis(2000);
        jedisPool = new JedisPool(config, redisHost, Integer.parseInt(redisPort),2000,psw);
        System.out.println(redisHost + "-----" + redisPort);
        return jedisPool;
    }
}
