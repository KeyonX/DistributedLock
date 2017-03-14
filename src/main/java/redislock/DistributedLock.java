package redislock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by KeyonX on 2016/12/20.
 * 基于redis，在分布式环境中，提供一个全局锁
 */
public class DistributedLock implements RedisLock {
    private static Logger logger = LoggerFactory.getLogger(DistributedLock.class);
    private String lockKey;
    private int maxLockTime = 5;//单位秒，超过此时间后，锁自动释放
    private String lockVersion;//确保每个线程只能释放自己申请的锁 TODO 全局唯一性保证
    private long lockBeginTime;//获取到锁时的时间，单位毫秒
    DistributedLock(String lockKey, int seconds){
        this.lockKey = lockKey;
        maxLockTime = seconds;
        lockVersion = String.valueOf(System.currentTimeMillis());
    }

    public boolean tryLock(){
        return lock();
    }

    public boolean tryLock(long tryTime, TimeUnit timeUnit,long retryPeriod) throws Exception{
        validLock();
        long leftTryTime = timeUnit.toMillis(tryTime);
        long lastTime = System.currentTimeMillis();
        while(leftTryTime>=0){
            if(lock()){
                lockBeginTime = System.currentTimeMillis();
                return true;
            }
            timeUnit.sleep(retryPeriod);
            long now = System.currentTimeMillis();
            leftTryTime -= now-lastTime;
            lastTime = now;
        }
        logger.warn("获取RedisLock超时,lockKey={}",lockKey);
        return false;
    }

    private boolean lock(){
        Jedis jedis = null;
        try {
            jedis = RedisLockFactory.pool.getResource();
            StringBuilder luaCommand = new StringBuilder();
            luaCommand.append("local r = redis.call('SET',KEYS[1],ARGV[1],ARGV[2],ARGV[3],ARGV[4]);");
            luaCommand.append("return r");
            List<String> keys = new ArrayList<String>();
            keys.add(lockKey);
            List<String> args = new ArrayList<String>();
            args.add(lockVersion);
            args.add("EX");
            args.add(String.valueOf(maxLockTime));
            args.add("NX");
            String result = String.valueOf(jedis.eval(luaCommand.toString(), keys, args));
            if ("OK".equals(result)) {
                return true;
            }
        } catch (Exception e) {
            logger.error("获取锁异常,lockKey={}", lockKey);
        } finally {
            RedisLockFactory.pool.returnResource(jedis);
        }
        return false;
    }

    public void unLock() {
        Jedis jedis = null;
        try {
            jedis = RedisLockFactory.pool.getResource();
            StringBuilder luaCommand = new StringBuilder();
            luaCommand.append("if redis.call('get',KEYS[1]) == ARGV[1] then\n");
            luaCommand.append("return redis.call('del',KEYS[1])\n");
            luaCommand.append("else\n");
            luaCommand.append("return 0\n");
            luaCommand.append("end");
            List<String> keys = new ArrayList<String>();
            keys.add(lockKey);
            List<String> args = new ArrayList<String>();
            args.add(lockVersion);
            String result = String.valueOf(jedis.eval(luaCommand.toString(), keys, args));
            if ("0".equals(result)) {
                logger.warn("锁key={}释放时已过期",lockKey);
            }
        } finally {
            RedisLockFactory.pool.returnResource(jedis);
        }
        long lockLastTime = System.currentTimeMillis()-lockBeginTime;
        if(lockLastTime>maxLockTime*500){//锁的持续时间超过最大持续时间的50%
            logger.warn(String.format("RedisLock持续时间为%s,lockKey=%s,ver=%s",lockLastTime,lockKey,lockVersion));
        }
    }

    /**
     * 校验锁的异常持有(因持有锁的服务不可用，导致锁没有释放),如果是，则删除锁
     */
    private void validLock(){
        Jedis jedis = null;
        try {
            jedis = RedisLockFactory.pool.getResource();
            StringBuilder luaCommand = new StringBuilder();
            luaCommand.append("local ttl = tonumber(redis.call('ttl',KEYS[1]));\n");
            luaCommand.append("if ttl == -1 then\n");
            luaCommand.append("redis.call('DEL',KEYS[1]);\n");
            luaCommand.append("end");
            List<String> keys = new ArrayList<String>();
            keys.add(lockKey);
            jedis.eval(luaCommand.toString(), keys,new ArrayList<String>());
        } catch (Exception e) {
            logger.error("validLock异常,lockKey={}", lockKey);
        } finally {
            RedisLockFactory.pool.returnResource(jedis);
        }
    }

}
