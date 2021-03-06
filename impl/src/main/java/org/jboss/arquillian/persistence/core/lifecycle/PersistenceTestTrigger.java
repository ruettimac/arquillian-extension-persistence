/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.persistence.core.lifecycle;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.jboss.arquillian.core.api.Event;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.persistence.core.configuration.PersistenceConfiguration;
import org.jboss.arquillian.persistence.core.event.AfterPersistenceTest;
import org.jboss.arquillian.persistence.core.event.BeforePersistenceClass;
import org.jboss.arquillian.persistence.core.event.BeforePersistenceTest;
import org.jboss.arquillian.persistence.core.event.InitializeConfiguration;
import org.jboss.arquillian.persistence.core.exception.ContextNotAvailableException;
import org.jboss.arquillian.persistence.core.exception.DataSourceNotFoundException;
import org.jboss.arquillian.persistence.core.metadata.MetadataExtractor;
import org.jboss.arquillian.persistence.core.metadata.PersistenceExtensionEnabler;
import org.jboss.arquillian.persistence.core.metadata.PersistenceExtensionFeatureResolver;
import org.jboss.arquillian.test.spi.annotation.ClassScoped;
import org.jboss.arquillian.test.spi.annotation.TestScoped;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.Before;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;

/**
 * Determines if persistence extension should be triggered for the given
 * test class.
 *
 * @author <a href="mailto:bartosz.majsak@gmail.com">Bartosz Majsak</a>
 *
 */
public class PersistenceTestTrigger
{

   @Inject @ClassScoped
   private InstanceProducer<MetadataExtractor> metadataExtractorProducer;

   @Inject @ClassScoped
   private InstanceProducer<PersistenceExtensionEnabler> persistenceExtensionEnabler;

   @Inject @TestScoped
   private InstanceProducer<PersistenceExtensionFeatureResolver> persistenceExtensionFeatureResolverProvider;

   @Inject @TestScoped
   private InstanceProducer<javax.sql.DataSource> dataSourceProducer;

   @Inject
   private Instance<Context> contextInstance;

   @Inject
   private Instance<PersistenceConfiguration> configurationInstance;

   @Inject
   private Event<BeforePersistenceTest> beforePersistenceTestEvent;

   @Inject
   private Event<AfterPersistenceTest> afterPersistenceTestEvent;

   @Inject
   private Event<InitializeConfiguration> initializeConfigurationEvent;

   @Inject
   private Event<BeforePersistenceClass> beforePersistenceClassEvent;

   public void beforeClass(@Observes BeforeClass beforeClass)
   {
      metadataExtractorProducer.set(new MetadataExtractor(beforeClass.getTestClass()));
      persistenceExtensionEnabler.set(new PersistenceExtensionEnabler(metadataExtractorProducer.get()));
      if (persistenceExtensionEnabler.get().shouldPersistenceExtensionBeActivated())
      {
         initializeConfigurationEvent.fire(new InitializeConfiguration());
         beforePersistenceClassEvent.fire(new BeforePersistenceClass(beforeClass.getTestClass()));
      }
   }

   public void beforeTest(@Observes(precedence = 25) Before beforeTestEvent)
   {
      PersistenceConfiguration persistenceConfiguration = configurationInstance.get();
      persistenceExtensionFeatureResolverProvider.set(new PersistenceExtensionFeatureResolver(beforeTestEvent.getTestMethod(), metadataExtractorProducer.get(), persistenceConfiguration));

      if (persistenceExtensionEnabler.get().shouldPersistenceExtensionBeActivated())
      {
         createDataSource();
         beforePersistenceTestEvent.fire(new BeforePersistenceTest(beforeTestEvent));
      }

   }

   public void afterTest(@Observes(precedence = 25) After afterTestEvent)
   {
      if (persistenceExtensionEnabler.get().shouldPersistenceExtensionBeActivated())
      {
         afterPersistenceTestEvent.fire(new AfterPersistenceTest(afterTestEvent));
      }
   }

   // Private methods

   private void createDataSource()
   {
      String dataSourceName = persistenceExtensionFeatureResolverProvider.get().getDataSourceName();
      dataSourceProducer.set(loadDataSource(dataSourceName));
   }

   private DataSource loadDataSource(String dataSourceName)
   {
      try
      {
         final Context context = contextInstance.get();
         if (context == null)
         {
            throw new ContextNotAvailableException("No Naming Context available.");
         }
         return (DataSource) context.lookup(dataSourceName);
      }
      catch (NamingException e)
      {
         throw new DataSourceNotFoundException("Unable to find data source for given name: " + dataSourceName, e);
      }
   }

}
