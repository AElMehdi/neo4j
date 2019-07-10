/*
 * Copyright (c) 2002-2019 "Neo4j,"
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
package org.neo4j.procedure.impl;

import org.neo4j.function.ThrowingFunction;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.api.procedure.Context;
import org.neo4j.procedure.ProcedureTransaction;

public class ProcedureTransactionProvider implements ThrowingFunction<Context,ProcedureTransaction,ProcedureException>
{
    @Override
    public ProcedureTransaction apply( Context ctx ) throws ProcedureException
    {
        KernelTransaction ktx = ctx.kernelTransaction();
        return new ProcedureTransactionImpl( ktx );
    }

    private static class ProcedureTransactionImpl implements ProcedureTransaction
    {
        private final KernelTransaction ktx;

        ProcedureTransactionImpl( KernelTransaction ktx )
        {
            this.ktx = ktx;
        }

        @Override
        public void terminate()
        {
            ktx.markForTermination( Status.Transaction.Terminated );
        }

        @Override
        public void failure()
        {
            ktx.failure();
        }
    }
}