package redislock;

import java.util.concurrent.TimeUnit;

/**
 * Created by KeyonX on 2016/12/20.
 */
public interface RedisLock {

    boolean tryLock();

    boolean tryLock(long time, TimeUnit unit,long retryPeriod) throws Exception;

    void unLock();
}
