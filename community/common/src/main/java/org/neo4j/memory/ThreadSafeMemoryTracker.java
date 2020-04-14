/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.memory;

import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static org.neo4j.memory.MemoryPools.NO_TRACKING;
import static org.neo4j.util.Preconditions.checkState;
import static org.neo4j.util.Preconditions.requireNonNegative;
import static org.neo4j.util.Preconditions.requirePositive;

/**
 * Memory allocation tracker that can be used in local context that required
 * tracking of memory that is independent from global. You can impose a limit
 * on the total number of allocated bytes.
 * <p>
 * To reduce contention on the parent tracker, locally reserved bytes are batched
 * from the parent to a local pool. Once the pool is used up, new bytes will be
 * reserved. Calling {@link #reset()} will give back all the reserved bytes to
 * the parent. Forgetting to call this will "leak" bytes and starve the database
 * of allocations.
 */
public class ThreadSafeMemoryTracker implements MemoryTracker
{
    private static final long NO_LIMIT = Long.MAX_VALUE;
    private static final long DEFAULT_GRAB_SIZE = 1024;

    /**
     * Imposes limits on a {@link MemoryGroup} level, e.g. global maximum transactions size
     */
    private final MemoryPool memoryGroupPool;

    /**
     * The chunk size to reserve from the memory pool
     */
    private final long grabSize;

    /**
     * A per tracker limit.
     */
    private volatile long localHeapBytesLimit;

    /**
     * Number of bytes we are allowed to use on the heap. If this run out, we need to reserve more from the parent.
     */
    private final AtomicLong localHeapPool;

    /**
     * The current size of the tracked heap
     */
    private final AtomicLong allocatedBytesHeap;

    /**
     * The currently allocated off heap
     */
    private final AtomicLong allocatedBytesDirect;

    /**
     * The heap high water mark, i.e. the maximum observed allocated heap bytes
     */
    private final AtomicLong heapHighWaterMark;

    public ThreadSafeMemoryTracker()
    {
        this( NO_TRACKING, NO_LIMIT, DEFAULT_GRAB_SIZE );
    }

    public ThreadSafeMemoryTracker( MemoryPool memoryGroupPool, long localHeapBytesLimit, long grabSize )
    {
        this.memoryGroupPool = requireNonNull( memoryGroupPool );
        this.localHeapBytesLimit = localHeapBytesLimit == 0 ? NO_LIMIT : requireNonNegative( localHeapBytesLimit );
        this.grabSize = requireNonNegative( grabSize );
        this.localHeapPool = new AtomicLong();
        this.allocatedBytesHeap = new AtomicLong();
        this.allocatedBytesDirect = new AtomicLong();
        this.heapHighWaterMark = new AtomicLong();
    }

    @Override
    public void allocateNative( long bytes )
    {
        this.allocatedBytesDirect.addAndGet( bytes );
        this.memoryGroupPool.reserveNative( bytes );
    }

    @Override
    public void releaseNative( long bytes )
    {
        this.allocatedBytesDirect.addAndGet( -bytes );
        this.memoryGroupPool.releaseNative( bytes );
    }

    @Override
    public void allocateHeap( long bytes )
    {
        if ( bytes == 0 )
        {
            return;
        }
        requirePositive( bytes );

        long allocatedHeap = allocatedBytesHeap.addAndGet( bytes );

        if ( allocatedHeap > localHeapBytesLimit )
        {
            throw new MemoryLimitExceeded( bytes, localHeapBytesLimit, allocatedHeap - bytes );
        }

        if ( allocatedHeap > heapHighWaterMark.get() )
        {
            heapHighWaterMark.set( allocatedHeap );
        }

        if ( allocatedHeap > localHeapPool.get() )
        {
            long grab = max( bytes, grabSize );
            reserveHeap( grab );
        }
    }

    @Override
    public void releaseHeap( long bytes )
    {
        requireNonNegative( bytes );
        allocatedBytesHeap.addAndGet( -bytes );
    }

    @Override
    public long heapHighWaterMark()
    {
        return heapHighWaterMark.get();
    }

    /**
     * @return number of used bytes.
     */
    @Override
    public long usedNativeMemory()
    {
        return allocatedBytesDirect.get();
    }

    @Override
    public long estimatedHeapMemory()
    {
        return allocatedBytesHeap.get();
    }

    @Override
    public void reset()
    {
        checkState( allocatedBytesDirect.get() == 0, "Potential direct memory leak" );
        memoryGroupPool.releaseHeap( localHeapPool.get() );
        localHeapPool.set( 0 );
        allocatedBytesHeap.set( 0 );
        heapHighWaterMark.set( 0 );
    }

    public void setHeapLimit( long localHeapBytesLimit )
    {
        this.localHeapBytesLimit = validateHeapLimit( localHeapBytesLimit );
    }

    /**
     * Will reserve heap on the parent tracker.
     *
     * @param size heap space to reserve for the local pool
     * @throws MemoryLimitExceeded if not enough free memory
     */
    private void reserveHeap( long size )
    {
        memoryGroupPool.reserveHeap( size );
        localHeapPool.addAndGet( size );
    }

    private static long validateHeapLimit( long localHeapBytesLimit )
    {
        return localHeapBytesLimit == 0 ? NO_LIMIT : requireNonNegative( localHeapBytesLimit );
    }
}