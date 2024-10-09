/*!
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 */

package org.pentaho.common.ui.metadata.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.json.JSONException;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.pentaho.common.ui.metadata.model.IColumn;
import org.pentaho.common.ui.metadata.model.Operator;
import org.pentaho.common.ui.metadata.model.impl.Category;
import org.pentaho.common.ui.metadata.model.impl.Column;
import org.pentaho.common.ui.metadata.model.impl.Condition;
import org.pentaho.common.ui.metadata.model.impl.Model;
import org.pentaho.common.ui.metadata.model.impl.ModelInfo;
import org.pentaho.common.ui.metadata.model.impl.Order;
import org.pentaho.common.ui.metadata.model.impl.Parameter;
import org.pentaho.common.ui.metadata.model.impl.Query;
import org.pentaho.commons.connection.marshal.MarshallableResultSet;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.metadata.model.Domain;
import org.pentaho.metadata.model.concept.types.AggregationType;
import org.pentaho.metadata.model.concept.types.DataType;
import org.pentaho.metadata.query.model.CombinationType;
import org.pentaho.metadata.query.model.util.QueryXmlHelper;
import org.pentaho.metadata.repository.IMetadataDomainRepository;
import org.pentaho.metadata.repository.InMemoryMetadataDomainRepository;
import org.pentaho.metadata.util.XmiParser;
import org.pentaho.platform.api.data.IDBDatasourceService;
import org.pentaho.platform.api.engine.IPentahoDefinableObjectFactory;
import org.pentaho.platform.api.engine.IPluginManager;
import org.pentaho.platform.api.engine.IPluginProvider;
import org.pentaho.platform.api.engine.ISolutionEngine;
import org.pentaho.platform.api.repository2.unified.IUnifiedRepository;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.StandaloneSession;
import org.pentaho.platform.engine.core.system.boot.PlatformInitializationException;
import org.pentaho.platform.engine.services.connection.datasource.dbcp.JndiDatasourceService;
import org.pentaho.platform.engine.services.solution.SolutionEngine;
import org.pentaho.platform.plugin.action.kettle.KettleSystemListener;
import org.pentaho.platform.plugin.services.connections.sql.SQLConnection;
import org.pentaho.platform.plugin.services.pluginmgr.DefaultPluginManager;
import org.pentaho.platform.plugin.services.pluginmgr.FileSystemXmlPluginProvider;
import org.pentaho.platform.plugin.services.pluginmgr.PluginAdapter;
import org.pentaho.platform.repository2.unified.fs.FileSystemBackedUnifiedRepository;
import org.pentaho.pms.core.exception.PentahoMetadataException;
import org.pentaho.test.platform.engine.core.MicroPlatform;

import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;

import static org.pentaho.common.ui.IntegrationTestConstants.SOLUTION_PATH;

@SuppressWarnings( { "all" } )
public class MetadataServiceIT {

  private static final String PENTAHO_XML_PATH = "/system/pentaho.xml"; //$NON-NLS-1$

  private static final String SYSTEM_FOLDER = "/system"; //$NON-NLS-1$

  private static final String SOLUTION = "testsolution"; //$NON-NLS-1$

  private static MicroPlatform microPlatform = null;
  private static Mockery context = new JUnit4Mockery();
  private static IUnifiedRepository repository;

  @BeforeClass
  public static void initPlatform() throws Exception {

    if ( microPlatform == null || !microPlatform.isInitialized() ) {
      microPlatform = new MicroPlatform(SOLUTION_PATH);
      microPlatform.define( ISolutionEngine.class, SolutionEngine.class );

      repository = new FileSystemBackedUnifiedRepository();
      ( (FileSystemBackedUnifiedRepository) repository ).setRootDir( new File(SOLUTION_PATH) );

      microPlatform.defineInstance( IUnifiedRepository.class, repository );

      IMetadataDomainRepository domainRepository = new InMemoryMetadataDomainRepository();
      domainRepository.storeDomain(
          getDomain( "steel-wheels", SOLUTION_PATH + "/steel-wheels/metadata.xmi" ), true );

      microPlatform.defineInstance( IMetadataDomainRepository.class, domainRepository );
      microPlatform.define( IPluginManager.class, DefaultPluginManager.class,
          IPentahoDefinableObjectFactory.Scope.GLOBAL );
      microPlatform.define( IPluginProvider.class, FileSystemXmlPluginProvider.class );

      microPlatform.define( "connection-SQL", SQLConnection.class );
      microPlatform.define( IDBDatasourceService.class, JndiDatasourceService.class );

      microPlatform.addLifecycleListener( new PluginAdapter() );

      try {
        microPlatform.start();
      } catch ( PlatformInitializationException e ) {
        e.printStackTrace();
      }
    }

    // JNDI
    System.setProperty( "java.naming.factory.initial", "org.osjava.sj.SimpleContextFactory" ); //$NON-NLS-1$ //$NON-NLS-2$
    System.setProperty( "org.osjava.sj.root", SOLUTION_PATH + "/system/simple-jndi" ); //$NON-NLS-1$ //$NON-NLS-2$
    System.setProperty( "org.osjava.sj.delimiter", "/" ); //$NON-NLS-1$ //$NON-NLS-2$
  }

  private static final Domain getDomain( final String domainId, final String domainFile ) throws Exception {
    final InputStream in = new FileInputStream( domainFile );
    final XmiParser parser = new XmiParser();
    final Domain domain = parser.parseXmi( in );
    domain.setId( domainId );
    IOUtils.closeQuietly( in );
    return domain;
  }

  @Test
  public void testModelEquality() {
    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    MetadataService svc = new MetadataService();

    Model model = svc.loadModel( "steel-wheels", "BV_HUMAN_RESOURCES" );
    assertNotNull( "model should not be null", model );

    assertTrue( model.equals( model ) );
    assertFalse( model.equals( null ) );
    assertFalse( model.equals( this ) );
    assertFalse( model.equals( svc.loadModel( "steel-wheels", "BV_ORDERS" ) ) );

    Model model2 = new Model();
    Model model3 = new Model();

    assertTrue( model2.equals( model3 ) );
    model2.setCategories( new Category[] { new Category() } );
    assertFalse( model2.equals( model3 ) );
    model3.setCategories( new Category[] { new Category() } );
    assertFalse( model2.equals( model3 ) );
    model2.setCategories( null );
    assertFalse( model2.equals( model3 ) );
    model3.setCategories( null );
    assertTrue( model2.equals( model3 ) );
    model3.setId( "id" );
    assertFalse( model2.equals( model3 ) );
    model2.setId( "not id" );
    assertFalse( model2.equals( model3 ) );
    model2.setId( "id" );
    assertTrue( model2.equals( model3 ) );
    model3.setName( "name" );
    assertFalse( model2.equals( model3 ) );
    model2.setName( "not name" );
    assertFalse( model2.equals( model3 ) );
    model2.setName( "name" );
    assertTrue( model2.equals( model3 ) );

  }

  @Test
  public void testDomain() throws KettleException, PentahoMetadataException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    String queryString =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><mql><domain_id>steel-wheels</domain_id><model_id>BV_ORDERS</model_id><options><disable_distinct>false</disable_distinct></options><selections><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTLINE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTNAME</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTCODE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_QUANTITYORDERED</column><aggregation>SUM</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_TOTAL</column><aggregation>SUM</aggregation></selection></selections><constraints><constraint><operator>AND</operator><condition><![CDATA[[BC_CUSTOMER_W_TER_.BC_CUSTOMER_W_TER_COUNTRY] = \"Australia\"]]></condition></constraint></constraints><orders><order><direction>ASC</direction><view_id>CAT_ORDERS</view_id><column_id>BC_ORDERDETAILS_QUANTITYORDERED</column_id></order></orders></mql>";

    MetadataServiceUtil util = new MetadataServiceUtil();
    Domain domain = util.getDomainObject( queryString );
    assertNotNull( domain );
    util.setDomain( domain );
    assertEquals( domain, util.getDomain() );
  }

  @Test
  public void testQuery() throws KettleException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    Query query = getTestQuery();

    MarshallableResultSet results = svc.doQuery( query, -1 );
    assertNotNull( "results are null", results );
    assertEquals( "wrong number of column names", 6, results.getColumnNames().getColumnName().length );
    assertEquals( "wrong column name", "BC_PRODUCTS_PRODUCTLINE", results.getColumnNames().getColumnName()[0] );
    assertEquals( "wrong column type", "string", results.getColumnTypes().getColumnType()[0] );
    assertEquals( "wrong number of rows", 82, results.getRows().length );
    assertEquals( "wrong number of columns", 6, results.getRows()[0].getCell().length );
    assertEquals( "wrong value", "Planes", results.getRows()[0].getCell()[0] );
    assertEquals( "wrong number of column header sets", 0, results.getNumColumnHeaderSets() );
    assertEquals( "wrong number of row header sets", 0, results.getNumRowHeaderSets() );

  }

  @Test
  public void testQuery2() throws KettleException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    Query query = getTestQuery();
    // remove the condition value so that the default value it used
    query.getConditions()[0].setValue( new String[] { "Canada" } );

    MarshallableResultSet results = svc.doQuery( query, -1 );
    assertNotNull( "results are null", results );
    assertEquals( "wrong number of column names", 6, results.getColumnNames().getColumnName().length );
    assertEquals( "wrong column name", "BC_PRODUCTS_PRODUCTLINE", results.getColumnNames().getColumnName()[0] );
    assertEquals( "wrong column type", "string", results.getColumnTypes().getColumnType()[0] );
    assertEquals( "wrong number of rows", 48, results.getRows().length );
    assertEquals( "wrong number of columns", 6, results.getRows()[0].getCell().length );
    assertEquals( "wrong value", "Ships", results.getRows()[0].getCell()[0] );
    assertEquals( "wrong number of column header sets", 0, results.getNumColumnHeaderSets() );
    assertEquals( "wrong number of row header sets", 0, results.getNumRowHeaderSets() );

  }

  @Test
  public void testQuery3() throws KettleException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    Query query = getTestQuery();
    // Flag the condition as parameterized
    query.getConditions()[0].setValue( new String[] { "BC_CUSTOMER_W_TER_COUNTRY" } );
    query.getConditions()[0].setParameterized( true );
    Parameter param = new Parameter();
    param.setDefaultValue( new String[] { "Canada" } );
    param.setColumn( "BC_CUSTOMER_W_TER_COUNTRY" );
    param.setValue( new String[] { "Germany" } );
    query.setParameters( new Parameter[] { param } );

    MetadataServiceUtil util = new MetadataServiceUtil();
    org.pentaho.metadata.query.model.Query fullQuery = util.convertQuery( query );
    QueryXmlHelper helper = new QueryXmlHelper();
    String xml = helper.toXML( fullQuery );
    // System.out.println(xml);
    MarshallableResultSet results = svc.doQuery( query, -1 );
    assertNotNull( "results are null", results );
    assertEquals( "wrong number of column names", 6, results.getColumnNames().getColumnName().length );
    assertEquals( "wrong column name", "BC_PRODUCTS_PRODUCTLINE", results.getColumnNames().getColumnName()[0] );
    assertEquals( "wrong column type", "string", results.getColumnTypes().getColumnType()[0] );
    assertEquals( "wrong number of rows", 45, results.getRows().length );
    assertEquals( "wrong number of columns", 6, results.getRows()[0].getCell().length );
    assertEquals( "wrong value", "Planes", results.getRows()[0].getCell()[0] );
    assertEquals( "wrong number of column header sets", 0, results.getNumColumnHeaderSets() );
    assertEquals( "wrong number of row header sets", 0, results.getNumRowHeaderSets() );

  }

  private Query getTestQuery() {
    Query query = new Query();
    query.setDomainName( "steel-wheels" );
    query.setModelId( "BV_ORDERS" );
    query.setDisableDistinct( Boolean.FALSE );
    List<Column> cols = new ArrayList<Column>();
    Column col = new Column();
    col.setId( "BC_PRODUCTS_PRODUCTLINE" );
    col.setCategory( "CAT_PRODUCTS" );
    col.setSelectedAggType( "NONE" );
    cols.add( col );
    col = new Column();
    col.setId( "BC_CUSTOMER_W_TER_COUNTRY" );
    col.setCategory( "BC_CUSTOMER_W_TER_" );
    col.setSelectedAggType( "NONE" );
    cols.add( col );
    col = new Column();
    col.setId( "BC_PRODUCTS_PRODUCTNAME" );
    col.setCategory( "CAT_PRODUCTS" );
    col.setSelectedAggType( "NONE" );
    cols.add( col );
    col = new Column();
    col.setId( "BC_PRODUCTS_PRODUCTCODE" );
    col.setCategory( "CAT_PRODUCTS" );
    col.setSelectedAggType( "NONE" );
    cols.add( col );
    col = new Column();
    col.setId( "BC_ORDERDETAILS_QUANTITYORDERED" );
    col.setCategory( "CAT_ORDERS" );
    col.setSelectedAggType( "SUM" );
    cols.add( col );
    col = new Column();
    col.setId( "BC_ORDERDETAILS_TOTAL" );
    col.setCategory( "CAT_ORDERS" );
    col.setSelectedAggType( "SUM" );
    cols.add( col );
    query.setColumns( cols.toArray( new Column[cols.size()] ) );

    List<Condition> conditions = new ArrayList<Condition>();
    Condition condition = new Condition();
    condition.setColumn( "BC_CUSTOMER_W_TER_COUNTRY" );
    condition.setCategory( "BC_CUSTOMER_W_TER_" );
    condition.setOperator( Operator.EQUAL.name() );
    condition.setValue( new String[] { "Australia" } );
    conditions.add( condition );
    query.setConditions( conditions.toArray( new Condition[conditions.size()] ) );

    List<Order> orders = new ArrayList<Order>();
    Order order = new Order();
    order.setColumn( "BC_ORDERDETAILS_QUANTITYORDERED" );
    order.setCategory( "CAT_ORDERS" );
    order.setOrderType( "ASC" );
    orders.add( order );
    query.setOrders( orders.toArray( new Order[orders.size()] ) );

    return query;
  }

  @Test
  public void testXmlQuery1() throws KettleException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    String queryString =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><mql><domain_id>steel-wheels</domain_id><model_id>BV_ORDERS</model_id><options><disable_distinct>false</disable_distinct></options><selections><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTLINE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTNAME</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTCODE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_QUANTITYORDERED</column><aggregation>SUM</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_TOTAL</column><aggregation>SUM</aggregation></selection></selections><constraints><constraint><operator>AND</operator><condition><![CDATA[[BC_CUSTOMER_W_TER_.BC_CUSTOMER_W_TER_COUNTRY] = \"Australia\"]]></condition></constraint></constraints><orders><order><direction>ASC</direction><view_id>CAT_ORDERS</view_id><column_id>BC_ORDERDETAILS_QUANTITYORDERED</column_id></order></orders></mql>";
    MarshallableResultSet results = svc.doXmlQuery( queryString, -1 );
    assertNotNull( "results are null", results );
    assertEquals( "wrong number of column names", 5, results.getColumnNames().getColumnName().length );
    assertEquals( "wrong column name", "BC_PRODUCTS_PRODUCTLINE", results.getColumnNames().getColumnName()[0] );
    assertEquals( "wrong column type", "string", results.getColumnTypes().getColumnType()[0] );
    assertEquals( "wrong number of rows", 82, results.getRows().length );
    assertEquals( "wrong number of columns", 5, results.getRows()[0].getCell().length );
    assertEquals( "wrong value", "Planes", results.getRows()[0].getCell()[0] );
    assertEquals( "wrong number of column header sets", 0, results.getNumColumnHeaderSets() );
    assertEquals( "wrong number of row header sets", 0, results.getNumRowHeaderSets() );

  }

  @Test
  public void testXmlQuery2() throws KettleException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    String queryString =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><mql><domain_id>steel-wheels</domain_id><model_id>BV_ORDERS</model_id><options><disable_distinct>false</disable_distinct></options><selections><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTLINE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTNAME</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTCODE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_QUANTITYORDERED</column><aggregation>SUM</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_TOTAL</column><aggregation>SUM</aggregation></selection></selections><constraints><constraint><operator>AND</operator><condition><![CDATA[[BC_CUSTOMER_W_TER_.BC_CUSTOMER_W_TER_COUNTRY] = \"Australia\"]]></condition></constraint></constraints><orders><order><direction>ASC</direction><view_id>CAT_ORDERS</view_id><column_id>BC_ORDERDETAILS_QUANTITYORDERED</column_id></order></orders></mql>";
    MarshallableResultSet results = svc.doXmlQuery( queryString, 10 );
    assertNotNull( "results are null", results );
    assertEquals( "wrong number of rows", 10, results.getRows().length );
  }

  @Test
  public void testXmlQuery3() throws KettleException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    String queryString =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><mql><domain_id>bogus</domain_id><model_id>BV_ORDERS</model_id><options><disable_distinct>false</disable_distinct></options><selections><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTLINE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTNAME</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTCODE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_QUANTITYORDERED</column><aggregation>SUM</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_TOTAL</column><aggregation>SUM</aggregation></selection></selections><constraints><constraint><operator>AND</operator><condition><![CDATA[[BC_CUSTOMER_W_TER_.BC_CUSTOMER_W_TER_COUNTRY] = \"Australia\"]]></condition></constraint></constraints><orders><order><direction>ASC</direction><view_id>CAT_ORDERS</view_id><column_id>BC_ORDERDETAILS_QUANTITYORDERED</column_id></order></orders></mql>";
    MarshallableResultSet results = svc.doXmlQuery( queryString, 10 );
    assertNull( "results are not null", results );
  }

  public void testJsonQueryToJson2() throws KettleException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    String json =
        "{\"class\":\"org.pentaho.common.ui.metadata.model.impl.Query\",\"columns\":[{\"aggTypes\":[],\"category\":\"BC_CUSTOMER_W_TER_\",\"class\":\"org.pentaho.common.ui.metadata.model.impl.Column\",\"defaultAggType\":null,\"fieldType\":null,\"id\":\"BC_CUSTOMER_W_TER_COUNTRY\",\"name\":null,\"selectedAggType\":\"NONE\",\"type\":null}],\"conditions\":[{\"category\":\"BC_CUSTOMER_W_TER_\",\"class\":\"org.pentaho.common.ui.metadata.model.impl.Condition\",\"column\":\"BC_CUSTOMER_W_TER_COUNTRY\",\"combinationType\":\"AND\",\"operator\":\"EQUAL\",\"value\":[\"Australia\"]}],\"defaultParameterMap\":null,\"disableDistinct\":false,\"domainName\":\"steel-wheels\",\"modelId\":\"BV_ORDERS\",\"orders\":[{\"category\":\"BC_CUSTOMER_W_TER_\",\"class\":\"org.pentaho.common.ui.metadata.model.impl.Order\",\"column\":\"BC_CUSTOMER_W_TER_COUNTRY\",\"orderType\":\"ASC\"}],\"parameters\":[]}";
    json = svc.doJsonQueryToJson( json, -1 );

    assertNotNull( "results are null", json );
    // System.out.println(json);
    assertTrue( "wrong column name", json.indexOf( "BC_CUSTOMER_W_TER_COUNTRY" ) != -1 );
    assertTrue( "wrong column type", json.indexOf( "\"string\"" ) != -1 );
    assertTrue( "wrong value", json.indexOf( "Australia" ) != -1 );
  }

  @Test
  public void testXmlQueryJson2() throws KettleException, JSONException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    String queryString =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><mql><domain_id>bogus</domain_id><model_id>BV_ORDERS</model_id><options><disable_distinct>false</disable_distinct></options><selections><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTLINE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTNAME</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTCODE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_QUANTITYORDERED</column><aggregation>SUM</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_TOTAL</column><aggregation>SUM</aggregation></selection></selections><constraints/><orders/></mql>";
    String json = svc.doXmlQueryToJson( queryString, -1 );
    assertNull( "results are not null", json );

    MetadataServiceUtil util = new MetadataServiceUtil();
    assertNull( "results are not null", util.createCdaJson( null, null ) );

  }

  @Test
  public void testXmlQueryToCdaJson1() throws KettleException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    String queryString =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><mql><domain_id>steel-wheels</domain_id><model_id>BV_ORDERS</model_id><options><disable_distinct>false</disable_distinct></options><selections><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTLINE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTNAME</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTCODE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_QUANTITYORDERED</column><aggregation>SUM</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_TOTAL</column><aggregation>SUM</aggregation></selection></selections><constraints/><orders/></mql>";
    String json = svc.doXmlQueryToCdaJson( queryString, -1 );
    assertNotNull( "results are null", json );
    System.out.println( json );
    assertTrue( "wrong column name", json.indexOf( "BC_PRODUCTS_PRODUCTLINE" ) != -1 );
    assertTrue( "wrong column type", json.indexOf( "\"STRING\"" ) != -1 );
    assertTrue( "wrong value", json.indexOf( "Classic Cars" ) != -1 );
  }

  @Test
  public void testXmlQueryToCdaJson2() throws KettleException {

    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    KettleSystemListener.environmentInit( session );
    MetadataService svc = new MetadataService();

    String queryString =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><mql><domain_id>bogus</domain_id><model_id>BV_ORDERS</model_id><options><disable_distinct>false</disable_distinct></options><selections><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTLINE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTNAME</column><aggregation>NONE</aggregation></selection><selection><view>CAT_PRODUCTS</view><column>BC_PRODUCTS_PRODUCTCODE</column><aggregation>NONE</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_QUANTITYORDERED</column><aggregation>SUM</aggregation></selection><selection><view>CAT_ORDERS</view><column>BC_ORDERDETAILS_TOTAL</column><aggregation>SUM</aggregation></selection></selections><constraints/><orders/></mql>";
    String json = svc.doXmlQueryToCdaJson( queryString, -1 );
    assertNull( "results are not null", json );
  }

  @Test
  public void testGetModel1() {
    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    MetadataService svc = new MetadataService();

    Model model = svc.loadModel( "steel-wheels", "BV_ORDERS" );
    assertNotNull( "model should not be null", model );

    assertEquals( "domain id is wrong", "steel-wheels", model.getDomainId() );
    assertEquals( "model id is wrong", "BV_ORDERS", model.getId() );
    assertEquals( "model name is wrong", "Orders", model.getName() );
    assertEquals( "model description is wrong", "This model contains information about customers and their orders.",
        model.getDescription() );
    assertTrue( "model hash is wrong", model.hashCode() != 0 );

    assertEquals( "wrong number of categories", 4, model.getCategories().length );

    Category category = model.getCategories()[0];
    assertEquals( "wrong number of business columns", 13, category.getColumns().length );
    assertEquals( "category id is wrong", "BC_CUSTOMER_W_TER_", category.getId() );
    assertEquals( "category name is wrong", "Customer", category.getName() );

    IColumn column = category.getColumns()[0];
    assertEquals( "column default agg type is wrong", "NONE", column.getDefaultAggType().toString() );
    assertEquals( "column id is wrong", "BC_CUSTOMER_W_TER_TERRITORY", column.getId() );
    assertEquals( "column name is wrong", "Territory", column.getName() );
    assertEquals( "column selected agg type is wrong", "NONE", column.getSelectedAggType().toString() );
    assertEquals( "column type is wrong", "STRING", column.getType().toString() );
    assertEquals( "field type is wrong", "DIMENSION", column.getFieldType().toString() );
    assertEquals( "mask is wrong", null, column.getFormatMask() );
    assertEquals( "alignment is wrong", "LEFT", column.getHorizontalAlignment().toString() );
    assertEquals( "column agg types list is wrong size", 1, column.getAggTypes().length );

    category = model.getCategories()[1];
    assertEquals( "wrong number of business columns", 9, category.getColumns().length );
    assertEquals( "category id is wrong", "CAT_ORDERS", category.getId() );
    assertEquals( "category name is wrong", "Orders", category.getName() );

    column = category.getColumns()[6];
    assertEquals( "column default agg type is wrong", "SUM", column.getDefaultAggType().toString() );
    assertEquals( "column id is wrong", "BC_ORDERDETAILS_QUANTITYORDERED", column.getId() );
    assertEquals( "column name is wrong", "Quantity Ordered", column.getName() );
    assertEquals( "column selected agg type is wrong", "SUM", column.getSelectedAggType().toString() );
    assertEquals( "column type is wrong", "NUMERIC", column.getType().toString() );
    assertEquals( "field type is wrong", "FACT", column.getFieldType().toString() );
    assertEquals( "mask is wrong", "#,###.##", column.getFormatMask() );
    assertEquals( "alignment is wrong", "RIGHT", column.getHorizontalAlignment().toString() );
    assertEquals( "column agg types list is wrong size", 4, column.getAggTypes().length );

    column = category.getColumns()[8];
    assertEquals( "column default agg type is wrong", "SUM", column.getDefaultAggType().toString() );
    assertEquals( "column id is wrong", "BC_ORDERDETAILS_TOTAL", column.getId() );
    assertEquals( "column name is wrong", "Total", column.getName() );
    assertEquals( "column selected agg type is wrong", "SUM", column.getSelectedAggType().toString() );
    assertEquals( "column type is wrong", "NUMERIC", column.getType().toString() );
    assertEquals( "field type is wrong", "FACT", column.getFieldType().toString() );
    assertEquals( "mask is wrong", "$#,##0.00;($#,##0.00)", column.getFormatMask() );
    assertEquals( "alignment is wrong", "RIGHT", column.getHorizontalAlignment().toString() );
    assertEquals( "column agg types list is wrong size", 4, column.getAggTypes().length );

  }

  @Test
  public void testGetModel2() {
    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    MetadataService svc = new MetadataService();

    Model model = svc.loadModel( null, "BV_HUMAN_RESOURCES" );
    assertNull( "model should be null", model );
  }

  @Test
  public void testGetModel3() {
    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    MetadataService svc = new MetadataService();

    Model model = svc.loadModel( "steel-wheels", null );
    assertNull( "model should be null", model );
  }

  @Test
  public void testGetModel4() {
    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    MetadataService svc = new MetadataService();

    Model model = svc.loadModel( "bogud", "BV_HUMAN_RESOURCES" );
    assertNull( "model should be null", model );
  }

  @Test
  public void testGetModel5() {
    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    MetadataService svc = new MetadataService();

    Model model = svc.loadModel( "steel-wheels", "bogus" );
    assertNull( "model should be null", model );
  }

  @Test
  public void testListBusinessModels1() throws IOException {
    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    MetadataService svc = new MetadataService();

    ModelInfo[] models = svc.listBusinessModels( null, null );
    assertNotNull( models );
    assertTrue( "Wrong nuber of models returned", models.length > 2 );
    boolean found = false;
    for ( int idx = 0; idx < models.length; idx++ ) {
      if ( models[idx].getDomainId().equals( "steel-wheels" ) && models[idx].getModelId().equals( "BV_HUMAN_RESOURCES" ) ) {
        assertEquals( "Wrong domain id", "steel-wheels", models[idx].getDomainId() );
        assertEquals( "Wrong description", "This model contains information about Employees.", models[idx]
            .getModelDescription() );
        assertEquals( "Wrong model id", "BV_HUMAN_RESOURCES", models[idx].getModelId() );
        assertEquals( "Wrong model name", "Human Resources", models[idx].getModelName() );
        found = true;
      }
    }
    assertTrue( "model was not found", found );
  }

  @Test
  public void testListBusinessModels2() throws IOException {
    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    MetadataService svc = new MetadataService();

    ModelInfo[] models = svc.listBusinessModels( "steel-wheels", null );
    assertNotNull( models );
    assertEquals( "Wrong nuber of models returned", 3, models.length );
  }

  @Test
  public void testListBusinessModels3() throws IOException {
    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    MetadataService svc = new MetadataService();

    ModelInfo[] models = svc.listBusinessModels( "bogus", null );
    assertNotNull( models );
    assertEquals( "Wrong nuber of models returned", 0, models.length );
  }

  @Test
  public void testListBusinessModels4() throws IOException {
    // without a session we should not get any models back
    PentahoSessionHolder.removeSession();

    MetadataService svc = new MetadataService();

    ModelInfo[] models = svc.listBusinessModels( "bogus", null );
    assertNotNull( models );
    assertEquals( "Wrong nuber of models returned", 0, models.length );
  }

  @Test
  public void testListBusinessModelsJson() throws IOException {
    StandaloneSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );
    MetadataService svc = new MetadataService();

    String json = svc.listBusinessModelsJson( null, null );
    assertNotNull( json );
    // System.out.println(json);
    // convert to a list
    Object result = new JSONDeserializer().deserialize( json );
    List<ModelInfo> modelList = new JSONDeserializer<List<ModelInfo>>().deserialize( json );

    ModelInfo[] models = modelList.toArray( new ModelInfo[modelList.size()] );

    assertNotNull( models );
    assertTrue( "Wrong nuber of models returned", models.length > 2 );
    boolean found = false;
    for ( int idx = 0; idx < models.length; idx++ ) {
      if ( models[idx].getDomainId().equals( "steel-wheels" ) && models[idx].getModelId().equals( "BV_HUMAN_RESOURCES" ) ) {
        assertEquals( "Wrong domain id", "steel-wheels", models[idx].getDomainId() );
        assertEquals( "Wrong description", "This model contains information about Employees.", models[idx]
            .getModelDescription() );
        assertEquals( "Wrong model id", "BV_HUMAN_RESOURCES", models[idx].getModelId() );
        assertEquals( "Wrong model name", "Human Resources", models[idx].getModelName() );
        found = true;
      }
    }
    assertTrue( "model was not found", found );
  }

  @Test
  public void testParameter() {
    Parameter param = new Parameter();

    param.setDefaultValue( new String[] { "default" } );
    assertEquals( "default", param.getValue()[0] );
    assertEquals( "default", param.getDefaultValue()[0] );

    param.setValue( new String[] { "value" } );
    assertEquals( "value", param.getValue()[0] );
    assertEquals( "default", param.getDefaultValue()[0] );

    param.setType( "String" );
    assertEquals( "String", param.getType() );

    param.setName( "myparam" );
    assertEquals( "myparam", param.getName() );
  }

  @Test
  public void testCondition1() {

    Condition condition = new Condition();
    condition.setCategory( "cat" );
    condition.setColumn( "column" );
    condition.setCombinationType( CombinationType.AND.name() );
    condition.setOperator( Operator.EQUAL.name() );
    condition.setValue( new String[] { "bingo" } );
    condition.setParameterized( true );

    String str = condition.getCondition( DataType.STRING.getName() );
    assertEquals( "[cat.column] = [param:bingo]", str );

    condition.setValue( new String[] { "bingo" } );
    str = condition.getCondition( DataType.STRING.getName(), condition.getColumn() );
    assertEquals( "[cat.column] = [param:column]", str );

    condition.setValue( new String[] { "bingo" } );
    str = condition.getCondition( DataType.DATE.getName(), condition.getColumn() );
    assertEquals( "[cat.column] =DATEVALUE([param:bingo])", str );

    condition.setValue( new String[] { "bingo" } );
    str = condition.getCondition( DataType.STRING.getName(), null );
    assertEquals( "[cat.column] = \"bingo\"", str );

    condition.setValue( new String[] { "bingo" } );
    str = condition.getCondition( DataType.DATE.getName(), null );
    assertEquals( "[cat.column] =DATEVALUE(\"bingo\")", str );

    str = condition.getCondition( DataType.STRING.getName(), "myparam" );
    assertEquals( "[cat.column] = [param:myparam]", str );

  }

  @Test
  public void testCondition_aggregate() {
    Condition c = new Condition();
    c.setCategory( "cat" );
    c.setColumn( "column" );
    c.setCombinationType( CombinationType.OR.name() );
    c.setValue( new String[] { "testing" } );

    String str = c.getCondition( DataType.STRING.getName(), null );
    assertEquals( "[cat.column] = \"testing\"", str );

    c.setSelectedAggType( AggregationType.MINIMUM.name() );
    c.setValue( new String[] { "testing" } );
    str = c.getCondition( DataType.STRING.getName(), null );
    assertEquals( "[cat.column.MINIMUM] = \"testing\"", str );
  }

}
