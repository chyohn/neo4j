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
package org.neo4j.kernel.impl.newapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import org.neo4j.helpers.collection.Iterators;
import org.neo4j.internal.kernel.api.IndexReference;
import org.neo4j.internal.kernel.api.InternalIndexState;
import org.neo4j.internal.kernel.api.SchemaRead;
import org.neo4j.internal.kernel.api.SchemaWrite;
import org.neo4j.internal.kernel.api.TokenWrite;
import org.neo4j.internal.kernel.api.Transaction;
import org.neo4j.internal.kernel.api.Write;
import org.neo4j.internal.kernel.api.exceptions.schema.SchemaKernelException;
import org.neo4j.internal.schema.ConstraintDescriptor;
import org.neo4j.internal.schema.LabelSchemaDescriptor;
import org.neo4j.internal.schema.RelationTypeSchemaDescriptor;
import org.neo4j.values.storable.Values;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.helpers.collection.Iterators.asList;
import static org.neo4j.internal.kernel.api.IndexReference.NO_INDEX;

@SuppressWarnings( "Duplicates" )
public abstract class SchemaReadWriteTestBase<G extends KernelAPIWriteTestSupport> extends KernelAPIWriteTestBase<G>
{
    private int label, label2, type, prop1, prop2, prop3;

    @BeforeEach
    public void setUp() throws Exception
    {
        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            SchemaWrite schemaWrite = transaction.schemaWrite();
            Iterator<ConstraintDescriptor> constraints = schemaRead.constraintsGetAll();
            while ( constraints.hasNext() )
            {
                schemaWrite.constraintDrop( constraints.next() );
            }
            Iterator<IndexReference> indexes = schemaRead.indexesGetAll();
            while ( indexes.hasNext() )
            {
                schemaWrite.indexDrop( indexes.next() );
            }

            TokenWrite tokenWrite = transaction.tokenWrite();
            label = tokenWrite.labelGetOrCreateForName( "label" );
            label2 = tokenWrite.labelGetOrCreateForName( "label2" );
            type = tokenWrite.relationshipTypeGetOrCreateForName( "relationship" );
            prop1 = tokenWrite.propertyKeyGetOrCreateForName( "prop1" );
            prop2 = tokenWrite.propertyKeyGetOrCreateForName( "prop2" );
            prop3 = tokenWrite.propertyKeyGetOrCreateForName( "prop3" );
            transaction.success();
        }
    }

    @Test
    void shouldNotFindNonExistentIndex() throws Exception
    {
        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertThat( schemaRead.index( label, prop1 ), equalTo( IndexReference.NO_INDEX ) );
        }
    }

    @Test
    void shouldCreateIndex() throws Exception
    {
        IndexReference index;
        try ( Transaction transaction = beginTransaction() )
        {
            index = transaction.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertThat( schemaRead.index( label, prop1 ), equalTo( index ) );
        }
    }

    @Test
    void createdIndexShouldPopulateInTx() throws Exception
    {
        IndexReference index;
        try ( Transaction tx = beginTransaction() )
        {
            index = tx.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) );
            assertThat( tx.schemaRead().indexGetState( index ), equalTo( InternalIndexState.POPULATING ) );
            tx.success();
        }
    }

    @Test
    void shouldDropIndex() throws Exception
    {
        IndexReference index;
        try ( Transaction transaction = beginTransaction() )
        {
            index = transaction.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().indexDrop( index );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertThat( schemaRead.index( label, prop1 ), equalTo( NO_INDEX ) );
        }
    }

    @Test
    void shouldFailToDropNoIndex() throws Exception
    {
        try ( Transaction transaction = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () -> transaction.schemaWrite().indexDrop( IndexReference.NO_INDEX ) );
            transaction.success();
        }
    }

    @Test
    void shouldFailToDropNonExistentIndex() throws Exception
    {
        IndexReference index;
        try ( Transaction transaction = beginTransaction() )
        {
            index = transaction.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().indexDrop( index );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    transaction.schemaWrite().indexDrop( index ) );
            transaction.success();
        }
    }

    @Test
    void shouldFailIfExistingIndex() throws Exception
    {
        //Given
        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        //When
        try ( Transaction transaction = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    transaction.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) ) );
            transaction.success();
        }
    }

    @Test
    void shouldSeeIndexFromTransaction() throws Exception
    {
        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().indexCreate( labelDescriptor( label, prop2 ) );
            SchemaRead schemaRead = transaction.schemaRead();
            IndexReference index = schemaRead.index( label, prop2 );
            assertThat( index.properties(), equalTo( new int[]{prop2} ) );
            assertThat( 2, equalTo( Iterators.asList( schemaRead.indexesGetAll() ).size() ) );
        }
    }

    @Test
    void shouldNotSeeDroppedIndexFromTransaction() throws Exception
    {
        IndexReference index;
        try ( Transaction transaction = beginTransaction() )
        {
            index = transaction.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().indexDrop( index );
            SchemaRead schemaRead = transaction.schemaRead();
            assertThat( schemaRead.index( label, prop2 ), equalTo( NO_INDEX ) );
        }
    }

    @Test
    void shouldListAllIndexes() throws Exception
    {
        IndexReference toRetain;
        IndexReference toRetain2;
        IndexReference toDrop;
        IndexReference created;

        try ( Transaction tx = beginTransaction() )
        {
            toRetain = tx.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) );
            toRetain2 = tx.schemaWrite().indexCreate( labelDescriptor( label2, prop1 ) );
            toDrop = tx.schemaWrite().indexCreate( labelDescriptor( label, prop2 ) );
            tx.success();
        }

        try ( Transaction tx = beginTransaction() )
        {
            created = tx.schemaWrite().indexCreate( labelDescriptor( label2, prop2 ) );
            tx.schemaWrite().indexDrop( toDrop );

            Iterable<IndexReference> allIndexes = () -> tx.schemaRead().indexesGetAll();
            assertThat( allIndexes, containsInAnyOrder( toRetain, toRetain2, created ) );

            tx.success();
        }
    }

    @Test
    void shouldListIndexesByLabel() throws Exception
    {
        int wrongLabel;

        IndexReference inStore;
        IndexReference droppedInTx;
        IndexReference createdInTx;

        try ( Transaction tx = beginTransaction() )
        {
            wrongLabel = tx.tokenWrite().labelGetOrCreateForName( "wrongLabel" );
            tx.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( wrongLabel, prop1 ) );

            inStore = tx.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) );
            droppedInTx = tx.schemaWrite().indexCreate( labelDescriptor( label, prop2 ) );

            tx.success();
        }

        try ( Transaction tx = beginTransaction() )
        {
            createdInTx = tx.schemaWrite().indexCreate( labelDescriptor( label, prop3 ) );
            tx.schemaWrite().indexCreate( labelDescriptor( wrongLabel, prop2 ) );
            tx.schemaWrite().indexDrop( droppedInTx );

            Iterable<IndexReference> indexes = () -> tx.schemaRead().indexesGetForLabel( label );
            assertThat( indexes, containsInAnyOrder( inStore, createdInTx ) );

            tx.success();
        }
    }

    @Test
    void shouldCreateUniquePropertyConstraint() throws Exception
    {
        ConstraintDescriptor constraint;
        try ( Transaction transaction = beginTransaction() )
        {
            constraint = transaction.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertTrue( schemaRead.constraintExists( constraint ) );
            Iterator<ConstraintDescriptor> constraints = schemaRead.constraintsGetForLabel( label );
            assertThat( asList( constraints ), equalTo( singletonList( constraint ) ) );
        }
    }

    @Test
    void shouldDropUniquePropertyConstraint() throws Exception
    {
        ConstraintDescriptor constraint;
        try ( Transaction transaction = beginTransaction() )
        {
            constraint = transaction.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().constraintDrop( constraint );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertFalse( schemaRead.constraintExists( constraint ) );
            assertThat( asList( schemaRead.constraintsGetForLabel( label ) ), empty() );
        }
    }

    @Test
    void shouldFailToCreateUniqueConstraintIfExistingIndex() throws Exception
    {
        //Given
        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        //When
        try ( Transaction transaction = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    transaction.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1 ) ) );
            transaction.success();
        }
    }

    @Test
    void shouldFailToCreateIndexIfExistingUniqueConstraint() throws Exception
    {
        //Given
        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        //When
        try ( Transaction transaction = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    transaction.schemaWrite().indexCreate( labelDescriptor( label, prop1 ) ) );
            transaction.success();
        }
    }

    @Test
    void shouldFailToDropIndexIfExistingUniqueConstraint() throws Exception
    {
        //Given
        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        //When
        try ( Transaction transaction = beginTransaction() )
        {
            IndexReference index = transaction.schemaRead().index( label, prop1 );
            assertThrows( SchemaKernelException.class, () ->
                    transaction.schemaWrite().indexDrop( index ) );
            transaction.success();
        }
    }

    @Test
    void shouldFailToCreateUniqueConstraintIfConstraintNotSatisfied() throws Exception
    {
        //Given
        try ( Transaction transaction = beginTransaction() )
        {
            Write write = transaction.dataWrite();
            long node1 = write.nodeCreate();
            write.nodeAddLabel( node1, label );
            write.nodeSetProperty( node1, prop1, Values.intValue( 42 ) );
            long node2 = write.nodeCreate();
            write.nodeAddLabel( node2, label );
            write.nodeSetProperty( node2, prop1, Values.intValue( 42 ) );
            transaction.success();
        }

        //When
        try ( Transaction transaction = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    transaction.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1 ) ) );
        }
    }

    @Test
    void shouldSeeUniqueConstraintFromTransaction() throws Exception
    {
        ConstraintDescriptor existing;
        try ( Transaction transaction = beginTransaction() )
        {
            existing =
                    transaction.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            ConstraintDescriptor newConstraint =
                    transaction.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop2 ) );
            SchemaRead schemaRead = transaction.schemaRead();
            assertTrue( schemaRead.constraintExists( existing ) );
            assertTrue( schemaRead.constraintExists( newConstraint ) );
            assertThat( asList( schemaRead.constraintsGetForLabel( label ) ), containsInAnyOrder( existing, newConstraint ) );
        }
    }

    @Test
    void shouldNotSeeDroppedUniqueConstraintFromTransaction() throws Exception
    {
        ConstraintDescriptor existing;
        try ( Transaction transaction = beginTransaction() )
        {
            existing = transaction.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().constraintDrop( existing );
            SchemaRead schemaRead = transaction.schemaRead();
            assertFalse( schemaRead.constraintExists( existing ) );
            assertThat( asList( schemaRead.constraintsGetForLabel( label ) ), empty() );
        }
    }

    @Test
    void shouldCreateNodeKeyConstraint() throws Exception
    {
        ConstraintDescriptor constraint;
        try ( Transaction transaction = beginTransaction() )
        {
            constraint = transaction.schemaWrite().nodeKeyConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertTrue( schemaRead.constraintExists( constraint ) );
            Iterator<ConstraintDescriptor> constraints = schemaRead.constraintsGetForLabel( label );
            assertThat( asList( constraints ), equalTo( singletonList( constraint ) ) );
        }
    }

    @Test
    void shouldDropNodeKeyConstraint() throws Exception
    {
        ConstraintDescriptor constraint;
        try ( Transaction transaction = beginTransaction() )
        {
            constraint = transaction.schemaWrite().nodeKeyConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().constraintDrop( constraint );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertFalse( schemaRead.constraintExists( constraint ) );
            assertThat( asList( schemaRead.constraintsGetForLabel( label ) ), empty() );
        }
    }

    @Test
    void shouldFailToNodeKeyConstraintIfConstraintNotSatisfied() throws Exception
    {
        //Given
        try ( Transaction transaction = beginTransaction() )
        {
            Write write = transaction.dataWrite();
            long node = write.nodeCreate();
            write.nodeAddLabel( node, label );
            transaction.success();
        }

        //When
        try ( Transaction transaction = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    transaction.schemaWrite().nodeKeyConstraintCreate( labelDescriptor( label, prop1 ) ) );
        }
    }

    @Test
    void shouldSeeNodeKeyConstraintFromTransaction() throws Exception
    {
        ConstraintDescriptor existing;
        try ( Transaction transaction = beginTransaction() )
        {
            existing =
                    transaction.schemaWrite().nodeKeyConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            ConstraintDescriptor newConstraint =
                    transaction.schemaWrite().nodeKeyConstraintCreate( labelDescriptor( label, prop2 ) );
            SchemaRead schemaRead = transaction.schemaRead();
            assertTrue( schemaRead.constraintExists( existing ) );
            assertTrue( schemaRead.constraintExists( newConstraint ) );
            assertThat( asList( schemaRead.constraintsGetForLabel( label ) ), containsInAnyOrder( existing, newConstraint ) );
        }
    }

    @Test
    void shouldNotSeeDroppedNodeKeyConstraintFromTransaction() throws Exception
    {
        ConstraintDescriptor existing;
        try ( Transaction transaction = beginTransaction() )
        {
            existing = transaction.schemaWrite().nodeKeyConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().constraintDrop( existing );
            SchemaRead schemaRead = transaction.schemaRead();
            assertFalse( schemaRead.constraintExists( existing ) );
            assertThat( asList( schemaRead.constraintsGetForLabel( label ) ), empty() );

        }
    }

    @Test
    void shouldCreateNodePropertyExistenceConstraint() throws Exception
    {
        ConstraintDescriptor constraint;
        try ( Transaction transaction = beginTransaction() )
        {
            constraint = transaction.schemaWrite().nodePropertyExistenceConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertTrue( schemaRead.constraintExists( constraint ) );
            Iterator<ConstraintDescriptor> constraints = schemaRead.constraintsGetForLabel( label );
            assertThat( asList( constraints ), equalTo( singletonList( constraint ) ) );
        }
    }

    @Test
    void shouldDropNodePropertyExistenceConstraint() throws Exception
    {
        ConstraintDescriptor constraint;
        try ( Transaction transaction = beginTransaction() )
        {
            constraint = transaction.schemaWrite().nodePropertyExistenceConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().constraintDrop( constraint );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertFalse( schemaRead.constraintExists( constraint ) );
            assertThat( asList( schemaRead.constraintsGetForLabel( label ) ), empty() );
        }
    }

    @Test
    void shouldFailToCreatePropertyExistenceConstraintIfConstraintNotSatisfied() throws Exception
    {
        //Given
        try ( Transaction transaction = beginTransaction() )
        {
            Write write = transaction.dataWrite();
            long node = write.nodeCreate();
            write.nodeAddLabel( node, label );
            transaction.success();
        }

        //When
        try ( Transaction transaction = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    transaction.schemaWrite().nodePropertyExistenceConstraintCreate( labelDescriptor( label, prop1 ) ) );
        }
    }

    @Test
    void shouldSeeNodePropertyExistenceConstraintFromTransaction() throws Exception
    {
        ConstraintDescriptor existing;
        try ( Transaction transaction = beginTransaction() )
        {
            existing =
                    transaction.schemaWrite().nodePropertyExistenceConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            ConstraintDescriptor newConstraint =
                    transaction.schemaWrite().nodePropertyExistenceConstraintCreate( labelDescriptor( label, prop2 ) );
            SchemaRead schemaRead = transaction.schemaRead();
            assertTrue( schemaRead.constraintExists( existing ) );
            assertTrue( schemaRead.constraintExists( newConstraint ) );
            assertThat( asList( schemaRead.constraintsGetForLabel( label ) ), containsInAnyOrder( existing, newConstraint ) );
        }
    }

    @Test
    void shouldNotSeeDroppedNodePropertyExistenceConstraintFromTransaction() throws Exception
    {
        ConstraintDescriptor existing;
        try ( Transaction transaction = beginTransaction() )
        {
            existing = transaction.schemaWrite().nodePropertyExistenceConstraintCreate( labelDescriptor( label, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().constraintDrop( existing );
            SchemaRead schemaRead = transaction.schemaRead();
            assertFalse( schemaRead.constraintExists( existing ) );

            assertThat( schemaRead.index( label, prop2 ), equalTo( NO_INDEX ) );
            assertThat( asList( schemaRead.constraintsGetForLabel( label ) ), empty() );

        }
    }

    @Test
    void shouldCreateRelationshipPropertyExistenceConstraint() throws Exception
    {
        ConstraintDescriptor constraint;
        try ( Transaction transaction = beginTransaction() )
        {
            constraint = transaction.schemaWrite()
                    .relationshipPropertyExistenceConstraintCreate( typeDescriptor( type, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertTrue( schemaRead.constraintExists( constraint ) );
            Iterator<ConstraintDescriptor> constraints = schemaRead.constraintsGetForRelationshipType( type );
            assertThat( asList( constraints ), equalTo( singletonList( constraint ) ) );
        }
    }

    @Test
    void shouldDropRelationshipPropertyExistenceConstraint() throws Exception
    {
        ConstraintDescriptor constraint;
        try ( Transaction transaction = beginTransaction() )
        {
            constraint = transaction.schemaWrite()
                    .relationshipPropertyExistenceConstraintCreate( typeDescriptor( type, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().constraintDrop( constraint );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            SchemaRead schemaRead = transaction.schemaRead();
            assertFalse( schemaRead.constraintExists( constraint ) );
            assertThat( asList( schemaRead.constraintsGetForRelationshipType( type ) ), empty() );
        }
    }

    @Test
    void shouldFailToCreateRelationshipPropertyExistenceConstraintIfConstraintNotSatisfied() throws Exception
    {
        //Given
        try ( Transaction transaction = beginTransaction() )
        {
            Write write = transaction.dataWrite();
            write.relationshipCreate( write.nodeCreate(), type, write.nodeCreate() );
            transaction.success();
        }

        //When
        try ( Transaction transaction = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    transaction.schemaWrite().relationshipPropertyExistenceConstraintCreate( typeDescriptor( type, prop1 ) ) );
        }
    }

    @Test
    void shouldSeeRelationshipPropertyExistenceConstraintFromTransaction() throws Exception
    {
        ConstraintDescriptor existing;
        try ( Transaction transaction = beginTransaction() )
        {
            existing =
                    transaction.schemaWrite().relationshipPropertyExistenceConstraintCreate( typeDescriptor( type, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            ConstraintDescriptor newConstraint =
                    transaction.schemaWrite().relationshipPropertyExistenceConstraintCreate( typeDescriptor( type, prop2 ) );
            SchemaRead schemaRead = transaction.schemaRead();
            assertTrue( schemaRead.constraintExists( existing ) );
            assertTrue( schemaRead.constraintExists( newConstraint ) );
            assertThat( asList( schemaRead.constraintsGetForRelationshipType( type ) ),
                    containsInAnyOrder( existing, newConstraint ) );
        }
    }

    @Test
    void shouldNotSeeDroppedRelationshipPropertyExistenceConstraintFromTransaction() throws Exception
    {
        ConstraintDescriptor existing;
        try ( Transaction transaction = beginTransaction() )
        {
            existing = transaction.schemaWrite()
                    .relationshipPropertyExistenceConstraintCreate( typeDescriptor( type, prop1 ) );
            transaction.success();
        }

        try ( Transaction transaction = beginTransaction() )
        {
            transaction.schemaWrite().constraintDrop( existing );
            SchemaRead schemaRead = transaction.schemaRead();
            assertFalse( schemaRead.constraintExists( existing ) );

            assertThat( schemaRead.index( type, prop2 ), equalTo( NO_INDEX ) );
            assertThat( asList( schemaRead.constraintsGetForLabel( label ) ), empty() );

        }
    }

    @Test
    void shouldListAllConstraints() throws Exception
    {
        ConstraintDescriptor toRetain;
        ConstraintDescriptor toRetain2;
        ConstraintDescriptor toDrop;
        ConstraintDescriptor created;
        try ( Transaction tx = beginTransaction() )
        {
            toRetain = tx.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1 ) );
            toRetain2 = tx.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label2, prop1 ) );
            toDrop = tx.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop2 ) );
            tx.success();
        }

        try ( Transaction tx = beginTransaction() )
        {
            created = tx.schemaWrite().nodePropertyExistenceConstraintCreate( labelDescriptor( label, prop1 ) );
            tx.schemaWrite().constraintDrop( toDrop );

            Iterable<ConstraintDescriptor> allConstraints = () -> tx.schemaRead().constraintsGetAll();
            assertThat( allConstraints, containsInAnyOrder( toRetain, toRetain2, created ) );

            tx.success();
        }
    }

    @Test
    void shouldListConstraintsByLabel() throws Exception
    {
        int wrongLabel;

        ConstraintDescriptor inStore;
        ConstraintDescriptor droppedInTx;
        ConstraintDescriptor createdInTx;

        try ( Transaction tx = beginTransaction() )
        {
            wrongLabel = tx.tokenWrite().labelGetOrCreateForName( "wrongLabel" );
            tx.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( wrongLabel, prop1 ) );

            inStore = tx.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1 ) );
            droppedInTx = tx.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop2 ) );

            tx.success();
        }

        try ( Transaction tx = beginTransaction() )
        {
            createdInTx = tx.schemaWrite().nodePropertyExistenceConstraintCreate( labelDescriptor( label, prop1 ) );
            tx.schemaWrite().nodePropertyExistenceConstraintCreate( labelDescriptor( wrongLabel, prop1 ) );
            tx.schemaWrite().constraintDrop( droppedInTx );

            Iterable<ConstraintDescriptor> allConstraints = () -> tx.schemaRead().constraintsGetForLabel( label );
            assertThat( allConstraints, containsInAnyOrder( inStore, createdInTx ) );

            tx.success();
        }
    }

    @Test
    void shouldFailIndexCreateForRepeatedProperties() throws Exception
    {
        try ( Transaction tx = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    tx.schemaWrite().indexCreate( labelDescriptor( label, prop1, prop1 ) ) );
        }
    }

    @Test
    void shouldFailUniquenessConstraintCreateForRepeatedProperties() throws Exception
    {
        try ( Transaction tx = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    tx.schemaWrite().uniquePropertyConstraintCreate( labelDescriptor( label, prop1, prop1 ) ) );
        }
    }

    @Test
    void shouldFailNodeKeyCreateForRepeatedProperties() throws Exception
    {
        try ( Transaction tx = beginTransaction() )
        {
            assertThrows( SchemaKernelException.class, () ->
                    tx.schemaWrite().nodeKeyConstraintCreate( labelDescriptor( label, prop1, prop1 ) ) );
        }
    }

    protected abstract RelationTypeSchemaDescriptor typeDescriptor( int label, int... props );

    protected abstract LabelSchemaDescriptor labelDescriptor( int label, int... props );
}