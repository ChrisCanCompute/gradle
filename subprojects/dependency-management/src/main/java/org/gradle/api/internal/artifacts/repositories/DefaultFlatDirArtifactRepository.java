/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.repositories;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.GradleModuleMetadataSource;
import org.gradle.api.artifacts.repositories.IvyArtifactMetadataSource;
import org.gradle.api.artifacts.repositories.IvyDescriptorMetadataSource;
import org.gradle.api.internal.ExperimentalFeatures;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextualMetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.authentication.Authentication;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableIvyModuleResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.service.DefaultServiceRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultFlatDirArtifactRepository extends AbstractArtifactRepository implements FlatDirectoryArtifactRepository, ResolutionAwareRepository, PublicationAwareRepository {
    private final FileResolver fileResolver;
    private List<Object> dirs = new ArrayList<Object>();
    private final RepositoryTransportFactory transportFactory;
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final FileStore<ModuleComponentArtifactIdentifier> artifactFileStore;
    private final IvyContextManager ivyContextManager;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final FileResourceRepository fileResourceRepository;
    private final ModuleMetadataParser moduleMetadataParser;
    private final InstantiatorFactory instantiatorFactory;
    private final ExperimentalFeatures experimentalFeatures;

    public DefaultFlatDirArtifactRepository(FileResolver fileResolver,
                                            RepositoryTransportFactory transportFactory,
                                            LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                            FileStore<ModuleComponentArtifactIdentifier> artifactFileStore, IvyContextManager ivyContextManager,
                                            ImmutableModuleIdentifierFactory moduleIdentifierFactory, FileResourceRepository fileResourceRepository,
                                            ModuleMetadataParser moduleMetadataParser, InstantiatorFactory instantiatorFactory,
                                            ExperimentalFeatures experimentalFeatures) {
        super(createMetadataSources());
        this.fileResolver = fileResolver;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.ivyContextManager = ivyContextManager;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.fileResourceRepository = fileResourceRepository;
        this.moduleMetadataParser = moduleMetadataParser;
        this.instantiatorFactory = instantiatorFactory;
        this.experimentalFeatures = experimentalFeatures;
    }

    private static MetadataSourcesInternal createMetadataSources() {
        DefaultMetadataSources metadataSources = new DefaultMetadataSources();
        metadataSources.use(GradleModuleMetadataSource.class);
        metadataSources.use(IvyDescriptorMetadataSource.class);
        metadataSources.use(IvyArtifactMetadataSource.class);
        return metadataSources;
    }

    @Override
    public String getDisplayName() {
        Set<File> dirs = getDirs();
        if (dirs.isEmpty()) {
            return super.getDisplayName();
        }
        return super.getDisplayName() + '(' + Joiner.on(", ").join(dirs) + ')';
    }

    public Set<File> getDirs() {
        return fileResolver.resolveFiles(dirs).getFiles();
    }

    public void setDirs(Set<File> dirs) {
        setDirs((Iterable<?>) dirs);
    }

    public void setDirs(Iterable<?> dirs) {
        this.dirs = Lists.newArrayList(dirs);
    }

    public void dir(Object dir) {
        dirs(dir);
    }

    public void dirs(Object... dirs) {
        this.dirs.addAll(Arrays.asList(dirs));
    }

    public ModuleVersionPublisher createPublisher() {
        return createRealResolver();
    }

    public ConfiguredModuleComponentRepository createResolver() {
        return createRealResolver();
    }

    private IvyResolver createRealResolver() {
        Set<File> dirs = getDirs();
        if (dirs.isEmpty()) {
            throw new InvalidUserDataException("You must specify at least one directory for a flat directory repository.");
        }

        IvyResolver resolver = new IvyResolver(getName(), transportFactory.createTransport("file", getName(), Collections.<Authentication>emptyList()), locallyAvailableResourceFinder, false, artifactFileStore, moduleIdentifierFactory, null, getMetadataSources().asImmutable(createInjectorForMetadataSources()), DefaultIvyArtifactRepository.IvyMetadataArtifactProvider.INSTANCE);
        for (File root : dirs) {
            resolver.addArtifactLocation(root.toURI(), "/[artifact]-[revision](-[classifier]).[ext]");
            resolver.addArtifactLocation(root.toURI(), "/[artifact](-[classifier]).[ext]");
        }
        return resolver;
    }

    private Instantiator createInjectorForMetadataSources() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        registry.addProvider(new MetadataSourcesServices());
        return instantiatorFactory.inject(registry);
    }

    private class MetadataSourcesServices {
        MetaDataParser<MutableIvyModuleResolveMetadata> createParser() {
            return new IvyContextualMetaDataParser<MutableIvyModuleResolveMetadata>(ivyContextManager, new IvyXmlModuleDescriptorParser(new IvyModuleDescriptorConverter(moduleIdentifierFactory), moduleIdentifierFactory, fileResourceRepository));
        }

        MetadataArtifactProvider createMetadataArtifactProvider() {
            return DefaultIvyArtifactRepository.IvyMetadataArtifactProvider.INSTANCE;
        }

        FileResourceRepository createFileResourceRepository() {
            return fileResourceRepository;
        }

        ImmutableModuleIdentifierFactory createImmutableModuleIdentifierFactory() {
            return moduleIdentifierFactory;
        }

        ExperimentalFeatures createExperimentalFeatures() {
            return experimentalFeatures;
        }

        ModuleMetadataParser createModuleMetadataParser() {
            return moduleMetadataParser;
        }

        MutableModuleMetadataFactory<MutableIvyModuleResolveMetadata> createMutableIvyModuleResolveMetadataMutableModuleMetadataFactory() {
            return new IvyMutableModuleMetadataFactory(moduleIdentifierFactory);
        }
    }
}
