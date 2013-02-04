/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
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
package org.neo4j.kernel.impl.nioneo.store;

import static java.nio.ByteBuffer.wrap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.neo4j.helpers.UTF8.encode;
import static org.neo4j.helpers.collection.IteratorUtil.asCollection;
import static org.neo4j.helpers.collection.MapUtil.stringMap;
import static org.neo4j.kernel.impl.util.StringLogger.SYSTEM;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.kernel.DefaultIdGeneratorFactory;
import org.neo4j.kernel.DefaultTxHook;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.nioneo.store.SchemaRule.Kind;
import org.neo4j.test.impl.EphemeralFileSystemAbstraction;

public class SchemaStoreTest
{
    @Test
    public void serializationAndDeserialization() throws Exception
    {
        // GIVEN
        String propertyKey = "hello world";
        byte[] encodedPropertyKey = encode( propertyKey );
        int labelId = 0;
        ByteBuffer expected = wrap( new byte[4+1+2+encodedPropertyKey.length] );
        expected.putInt( labelId );
        expected.put( Kind.INDEX_RULE.id() );
        expected.putShort( (short) encodedPropertyKey.length );
        expected.put( encodedPropertyKey );
        long blockId = store.nextId();
        SchemaRule indexRule = new IndexRule( blockId, labelId, propertyKey );

        // WHEN
        Collection<DynamicRecord> records = store.allocateFrom( blockId, indexRule );
        for ( DynamicRecord record : records )
            store.updateRecord( record );
        
        // THEN
        assertEquals( 1, records.size() );
        assertTrue( Arrays.equals( expected.array(), records.iterator().next().getData() ) );

        Collection<DynamicRecord> readRecords = store.getRecords( blockId );
        assertEquals( 1, readRecords.size() );
        assertTrue( Arrays.equals( expected.array(), readRecords.iterator().next().getData() ) );
    }
    
    @Test
    public void storeAndLoadAllShortRules() throws Exception
    {
        // GIVEN
        Collection<SchemaRule> rules = Arrays.<SchemaRule>asList(
                new IndexRule( 1, 0, "name" ), new IndexRule( 2, 1, "age" ), new IndexRule( 3, 1, "name" ) );
        for ( SchemaRule rule : rules )
            storeRule( rule );

        // WHEN
        Collection<SchemaRule> readRules = asCollection( store.loadAll() );

        // THEN
        assertEquals( rules, readRules );
    }
    
    @Test
    public void storeAndLoadSingleLongRule() throws Exception
    {
        // GIVEN

        Collection<SchemaRule> rules = Arrays.<SchemaRule>asList( createLongIndexRule( 0, "bart" ) );
        for ( SchemaRule rule : rules )
            storeRule( rule );

        // WHEN
        Collection<SchemaRule> readRules = asCollection( store.loadAll() );

        // THEN
        assertEquals( rules, readRules );
    }

    @Test
    public void storeAndLoadAllLongRules() throws Exception
    {
        // GIVEN

        Collection<SchemaRule> rules = Arrays.<SchemaRule>asList(
                createLongIndexRule( 0, "name" ), createLongIndexRule( 1, "size" ), createLongIndexRule( 2, "hair" ) );
        for ( SchemaRule rule : rules )
            storeRule( rule );

        // WHEN
        Collection<SchemaRule> readRules = asCollection( store.loadAll() );

        // THEN
        assertEquals( rules, readRules );
    }

    private IndexRule createLongIndexRule( long label, String tag ) {
        StringBuilder builder = new StringBuilder( tag );
        for (int i = 1; i < SchemaStore.BLOCK_SIZE; i++)
            builder.append( (i % 2 == 1) ? "ding" : "dong" );
        return new IndexRule( 1, label, builder.toString() );
    }

    private long storeRule( SchemaRule rule )
    {
        long id = store.nextId();
        Collection<DynamicRecord> records = store.allocateFrom( id, rule );
        for ( DynamicRecord record : records )
            store.updateRecord( record );
        return id;
    }

    private Config config;
    private SchemaStore store;
    private EphemeralFileSystemAbstraction fileSystemAbstraction;
    private StoreFactory storeFactory;
    
    @Before
    public void before() throws Exception
    {
        config = new Config( stringMap() );
        fileSystemAbstraction = new EphemeralFileSystemAbstraction();
        DefaultIdGeneratorFactory idGeneratorFactory = new DefaultIdGeneratorFactory();
        DefaultWindowPoolFactory windowPoolFactory = new DefaultWindowPoolFactory();
        storeFactory = new StoreFactory( config, idGeneratorFactory, windowPoolFactory, fileSystemAbstraction, SYSTEM,
                new DefaultTxHook() );
        File file = new File( "schema-store" );
        storeFactory.createSchemaStore( file );
        store = storeFactory.newSchemaStore( file );
    }

    @After
    public void after() throws Exception
    {
        store.close();
        fileSystemAbstraction.shutdown();
    }
}