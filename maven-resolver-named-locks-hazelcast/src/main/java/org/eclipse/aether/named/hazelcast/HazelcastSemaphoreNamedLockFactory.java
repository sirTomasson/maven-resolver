package org.eclipse.aether.named.hazelcast;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.ISemaphore;
import org.eclipse.aether.named.support.AdaptedSemaphoreNamedLock;
import org.eclipse.aether.named.support.AdaptedSemaphoreNamedLock.AdaptedSemaphore;
import org.eclipse.aether.named.support.NamedLockFactorySupport;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Factory of {@link AdaptedSemaphoreNamedLock} instances using adapted Hazelcast {@link ISemaphore}. This class may
 * use {@link HazelcastInstance} backed by Hazelcast Server or Hazelcast Client.
 */
public class HazelcastSemaphoreNamedLockFactory
    extends NamedLockFactorySupport<ISemaphore>
{
    protected static final String NAME_PREFIX = "maven:resolver:";

    private final HazelcastInstance hazelcastInstance;

    private final BiFunction<HazelcastInstance, String, ISemaphore> semaphoreFunction;

    private final boolean destroySemaphore;

    private final boolean manageHazelcast;

    public HazelcastSemaphoreNamedLockFactory(
        final HazelcastInstance hazelcastInstance,
        final BiFunction<HazelcastInstance, String, ISemaphore> semaphoreFunction,
        final boolean destroySemaphore,
        final boolean manageHazelcast
    )
    {
        this.hazelcastInstance = hazelcastInstance;
        this.semaphoreFunction = semaphoreFunction;
        this.destroySemaphore = destroySemaphore;
        this.manageHazelcast = manageHazelcast;
    }

    @Override
    protected NamedLockHolder<ISemaphore> createLock( final String name )
    {
        ISemaphore semaphore = semaphoreFunction.apply( hazelcastInstance, name );
        return new NamedLockHolder<>(
                semaphore,
                new AdaptedSemaphoreNamedLock( name, this, new HazelcastSemaphore( semaphore ) )
        );
    }

    @Override
    public void shutdown()
    {
        if ( manageHazelcast )
        {
            hazelcastInstance.shutdown();
        }
    }

    @Override
    protected void destroyLock( final String name, final NamedLockHolder<ISemaphore> holder )
    {
        if ( destroySemaphore )
        {
            holder.getImplementation().destroy();
        }
    }

    private static final class HazelcastSemaphore implements AdaptedSemaphore
    {
        private final ISemaphore semaphore;

        private HazelcastSemaphore( final ISemaphore semaphore )
        {
            semaphore.init( Integer.MAX_VALUE );
            this.semaphore = semaphore;
        }

        @Override
        public boolean tryAcquire( final int perms, final long time, final TimeUnit unit )
            throws InterruptedException
        {
            return semaphore.tryAcquire( perms, time, unit );
        }

        @Override
        public void release( final int perms )
        {
            semaphore.release( perms );
        }
    }
}