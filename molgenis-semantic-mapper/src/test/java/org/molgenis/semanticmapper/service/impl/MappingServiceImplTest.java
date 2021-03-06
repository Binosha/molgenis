package org.molgenis.semanticmapper.service.impl;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.molgenis.data.meta.AttributeType.DECIMAL;
import static org.molgenis.data.meta.AttributeType.INT;
import static org.molgenis.data.meta.AttributeType.STRING;
import static org.molgenis.data.meta.AttributeType.XREF;
import static org.molgenis.data.meta.model.EntityType.AttributeRole.ROLE_ID;
import static org.molgenis.semanticmapper.meta.MappingProjectMetadata.DEPTH;
import static org.molgenis.semanticmapper.meta.MappingProjectMetadata.IDENTIFIER;
import static org.molgenis.semanticmapper.meta.MappingProjectMetadata.MAPPING_PROJECT;
import static org.molgenis.semanticmapper.meta.MappingProjectMetadata.MAPPING_TARGETS;
import static org.molgenis.semanticmapper.meta.MappingProjectMetadata.NAME;
import static org.molgenis.semanticmapper.service.impl.MappingServiceImpl.MAPPING_BATCH_SIZE;
import static org.molgenis.semanticmapper.service.impl.MappingServiceImpl.SOURCE;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.molgenis.data.AbstractMolgenisSpringTest;
import org.molgenis.data.DataService;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityManager;
import org.molgenis.data.MolgenisDataException;
import org.molgenis.data.Query;
import org.molgenis.data.Repository;
import org.molgenis.data.config.EntityBaseTestConfig;
import org.molgenis.data.meta.MetaDataService;
import org.molgenis.data.meta.model.Attribute;
import org.molgenis.data.meta.model.AttributeFactory;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.data.meta.model.EntityTypeFactory;
import org.molgenis.data.meta.model.Package;
import org.molgenis.data.meta.model.PackageFactory;
import org.molgenis.data.security.config.UserTestConfig;
import org.molgenis.data.security.permission.PermissionSystemService;
import org.molgenis.data.support.DynamicEntity;
import org.molgenis.data.util.EntityUtils;
import org.molgenis.jobs.Progress;
import org.molgenis.js.magma.JsMagmaScriptEvaluator;
import org.molgenis.semanticmapper.mapping.model.AttributeMapping;
import org.molgenis.semanticmapper.mapping.model.EntityMapping;
import org.molgenis.semanticmapper.mapping.model.MappingProject;
import org.molgenis.semanticmapper.mapping.model.MappingTarget;
import org.molgenis.semanticmapper.meta.MappingTargetMetadata;
import org.molgenis.semanticmapper.repository.MappingProjectRepository;
import org.molgenis.semanticmapper.service.AlgorithmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

@MockitoSettings(strictness = Strictness.LENIENT)
@ContextConfiguration(
    classes = {
      MappingServiceImplTest.Config.class,
      MappingServiceImpl.class,
      EntityBaseTestConfig.class
    })
class MappingServiceImplTest extends AbstractMolgenisSpringTest {

  private static final String TARGET_HOP_ENTITY = "HopEntity";
  private static final String SOURCE_GENE_ENTITY = "Gene";
  private static final String SOURCE_EXON_ENTITY = "Exon";

  @Autowired private DataService dataService;

  @Autowired private AlgorithmService algorithmService;

  @Autowired private MappingProjectRepository mappingProjectRepo;

  @Autowired private PermissionSystemService permissionSystemService;

  @Autowired private AttributeFactory attrMetaFactory;

  @Autowired private EntityManager entityManager;

  @Autowired private MappingServiceImpl mappingService;

  @Autowired private EntityTypeFactory entityTypeFactory;

  @Autowired private PackageFactory packageFactory;

  @Mock private Repository<Entity> hopRepo;

  @Mock private Repository<Entity> geneRepo;

  @Mock private Repository<Entity> exonRepo;

  @Mock private Progress progress;

  @Captor private ArgumentCaptor<List<Entity>> batchCaptor;

  private MetaDataService metaDataService;
  private EntityType hopMetaData;
  private EntityType geneMetaData;

  private Package package_;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void beforeMethod() {
    Mockito.reset(dataService);
    Mockito.reset(mappingProjectRepo);
    Mockito.reset(permissionSystemService);
    Mockito.reset(hopRepo);
    Mockito.reset(geneRepo);
    Mockito.reset(exonRepo);
    Mockito.reset(progress);

    package_ = packageFactory.create("package");

    hopMetaData = entityTypeFactory.create(TARGET_HOP_ENTITY).setPackage(package_);
    hopMetaData.setPackage(package_);
    hopMetaData.addAttribute(attrMetaFactory.create().setName("identifier"), ROLE_ID);
    hopMetaData.addAttribute(
        attrMetaFactory.create().setName("height").setDataType(DECIMAL).setNillable(false));

    geneMetaData = entityTypeFactory.create(SOURCE_GENE_ENTITY).setPackage(package_);
    geneMetaData.setLabel("Genes");
    geneMetaData.addAttribute(attrMetaFactory.create().setName("id"), ROLE_ID);
    geneMetaData.addAttribute(
        attrMetaFactory.create().setName("length").setDataType(DECIMAL).setNillable(false));

    EntityType exonMetaData = entityTypeFactory.create(SOURCE_EXON_ENTITY).setPackage(package_);
    exonMetaData.addAttribute(attrMetaFactory.create().setName("id"), ROLE_ID);
    exonMetaData.addAttribute(
        attrMetaFactory.create().setName("basepairs").setDataType(DECIMAL).setNillable(false));

    metaDataService = mock(MetaDataService.class);
    when(metaDataService.createRepository(
            argThat(obj -> obj != null && obj.getId().equals(geneMetaData.getId()))))
        .thenReturn(geneRepo);
    when(metaDataService.createRepository(
            argThat(obj -> obj != null && obj.getId().equals(SOURCE_EXON_ENTITY))))
        .thenReturn(exonRepo);

    when(hopRepo.getName()).thenReturn(TARGET_HOP_ENTITY);
    when(hopRepo.getEntityType()).thenReturn(hopMetaData);
    when(geneRepo.getName()).thenReturn(geneMetaData.getId());
    when(geneRepo.iterator()).thenReturn(Collections.<Entity>emptyList().iterator());
    when(geneRepo.getEntityType()).thenReturn(geneMetaData);
    when(exonRepo.getName()).thenReturn(SOURCE_EXON_ENTITY);
    when(exonRepo.iterator()).thenReturn(Collections.<Entity>emptyList().iterator());
    when(exonRepo.getEntityType()).thenReturn(exonMetaData);
    when(dataService.getMeta()).thenReturn(metaDataService);
    when(dataService.getEntityType(hopMetaData.getId())).thenReturn(hopMetaData);
    when(dataService.getRepository(TARGET_HOP_ENTITY)).thenReturn(hopRepo);
    Query<Entity> hopQ = mock(Query.class);
    when(hopRepo.query()).thenReturn(hopQ);
    when(hopQ.eq("source", geneMetaData.getId())).thenReturn(hopQ);
    when(hopQ.eq("source", SOURCE_EXON_ENTITY)).thenReturn(hopQ);
    when(hopQ.findAll()).thenAnswer(invocation -> Stream.empty());

    when(dataService.getEntityType(geneMetaData.getId())).thenReturn(geneMetaData);
    when(dataService.getRepository(geneMetaData.getId())).thenReturn(geneRepo);
    when(dataService.getEntityType(SOURCE_EXON_ENTITY)).thenReturn(exonMetaData);
    when(dataService.getRepository(SOURCE_EXON_ENTITY)).thenReturn(exonRepo);
  }

  @Test
  void testApplyMappingToEntity() {
    EntityMapping entityMapping = mock(EntityMapping.class);
    Entity sourceEntity = mock(Entity.class);
    EntityType targetMetaData = mock(EntityType.class);

    Entity mappedEntity = mock(Entity.class);
    when(entityManager.create(targetMetaData, EntityManager.CreationMode.POPULATE))
        .thenReturn(mappedEntity);

    assertEquals(
        mappedEntity,
        mappingService.applyMappingToEntity(entityMapping, sourceEntity, targetMetaData, 3));
  }

  @Test
  void testAddMappingProject() {
    String projectName = "test_project";
    MappingProject mappingProject =
        mappingService.addMappingProject(projectName, hopMetaData.getId(), 3);
    Mockito.verify(mappingProjectRepo, Mockito.times(1)).add(mappingProject);
    assertEquals(projectName, mappingProject.getName());
    List<MappingTarget> mappingTargets = mappingProject.getMappingTargets();
    assertEquals(1, mappingTargets.size());
    assertEquals(3, mappingProject.getDepth());
    assertEquals(hopMetaData, mappingTargets.get(0).getTarget());
  }

  @Test
  void testGetMappingProject() {
    String projectName = "test_project";

    MappingProject actualAddedMappingProject =
        mappingService.addMappingProject(projectName, hopMetaData.getId(), 3);

    String mappingTargetIdentifier =
        actualAddedMappingProject.getMappingTarget(hopMetaData.getId()).getIdentifier();
    MappingTarget expectedMappingTarget =
        getManualMappingTarget(mappingTargetIdentifier, emptyList());

    String mappingProjectIdentifier = actualAddedMappingProject.getIdentifier();
    MappingProject expectedMappingProject =
        getManualMappingProject(mappingProjectIdentifier, projectName, 3, expectedMappingTarget);

    Entity mappingTargetEntity = mock(Entity.class);
    when(mappingTargetEntity.getString(MappingTargetMetadata.IDENTIFIER))
        .thenReturn(mappingTargetIdentifier);
    when(mappingTargetEntity.getString(MappingTargetMetadata.TARGET))
        .thenReturn(hopMetaData.getId());
    when(mappingTargetEntity.getEntities(MappingTargetMetadata.ENTITY_MAPPINGS)).thenReturn(null);
    when(dataService.getEntityType(mappingTargetEntity.getString(MappingTargetMetadata.TARGET)))
        .thenReturn(hopMetaData);

    Entity mappingProjectEntity = mock(Entity.class);
    when(mappingProjectEntity.get(IDENTIFIER)).thenReturn(mappingProjectIdentifier);
    when(mappingProjectEntity.get(NAME)).thenReturn(projectName);
    when(mappingProjectEntity.get(DEPTH)).thenReturn(3);
    when(mappingProjectEntity.getEntities(MAPPING_TARGETS))
        .thenReturn(singletonList(mappingTargetEntity));
    when(mappingProjectEntity.get(MAPPING_TARGETS))
        .thenReturn(singletonList(expectedMappingTarget));

    when(dataService.findOneById(MAPPING_PROJECT, mappingProjectIdentifier))
        .thenReturn(mappingProjectEntity);
    when(mappingProjectRepo.getMappingProject(mappingProjectIdentifier))
        .thenReturn(actualAddedMappingProject);
    MappingProject retrievedMappingProject =
        mappingService.getMappingProject(mappingProjectIdentifier);

    assertEquals(expectedMappingProject, retrievedMappingProject);
  }

  @Test
  void testCloneMappingProjectString() {
    MappingProject mappingProject = mock(MappingProject.class);
    when(mappingProjectRepo.getMappingProject("0")).thenReturn(mappingProject);
    mappingService.cloneMappingProject("0");
    Mockito.verify(mappingProject).removeIdentifiers();
    Mockito.verify(mappingProjectRepo).add(mappingProject);
  }

  @Test
  void testCloneMappingProjectStringString() {
    MappingProject mappingProject = mock(MappingProject.class);
    when(mappingProjectRepo.getMappingProject("0")).thenReturn(mappingProject);
    mappingService.cloneMappingProject("0", "newName");
    Mockito.verify(mappingProject).removeIdentifiers();
    Mockito.verify(mappingProject).setName("newName");
    Mockito.verify(mappingProjectRepo).add(mappingProject);
  }

  @Test
  void testUpdateMappingProject() {
    MappingProject mappingProject = mock(MappingProject.class);
    mappingService.updateMappingProject(mappingProject);
    Mockito.verifyZeroInteractions(mappingProject);
    Mockito.verify(mappingProjectRepo).update(mappingProject);
  }

  @Test
  void testAddExistingTarget() {
    MappingProject mappingProject =
        mappingService.addMappingProject("Test123", hopMetaData.getId(), 3);
    assertThrows(IllegalStateException.class, () -> mappingProject.addTarget(hopMetaData));
  }

  @Test
  void createMetaWithNullParameters() {
    String targetRepositoryName = "target id";
    MappingTarget mappingTarget = mock(MappingTarget.class);
    when(mappingTarget.getTarget()).thenReturn(hopMetaData);
    when(dataService.hasRepository(targetRepositoryName)).thenReturn(true);
    when(dataService.getMeta()).thenReturn(metaDataService);
    EntityType targetRepositoryMetaData = entityTypeFactory.create(targetRepositoryName);
    targetRepositoryMetaData.addAttribute(
        attrMetaFactory.create().setName("ID").setDataType(STRING), ROLE_ID);
    targetRepositoryMetaData.setPackage(package_);
    when(metaDataService.getEntityType(targetRepositoryName))
        .thenReturn(Optional.of(targetRepositoryMetaData));

    EntityType targetMetadata =
        mappingService.createTargetMetadata(mappingTarget, targetRepositoryName, null, null, null);

    assertEquals("target id", targetMetadata.getId());
    assertEquals("target id", targetMetadata.getLabel());
    assertEquals(package_, targetMetadata.getPackage());
    assertNull(targetMetadata.getAttribute(SOURCE));
  }

  @Test
  void createMetaWithNonNullParameters() {
    MappingTarget mappingTarget = mock(MappingTarget.class);
    when(mappingTarget.getTarget()).thenReturn(hopMetaData);

    Package targetPackage = mock(Package.class);
    when(metaDataService.getPackage("targetPackage")).thenReturn(Optional.of(targetPackage));

    EntityType targetMetadata =
        mappingService.createTargetMetadata(
            mappingTarget, "test", "targetPackage", "target label", true);
    assertEquals("test", targetMetadata.getId());
    assertEquals("target label", targetMetadata.getLabel());
    assertEquals(targetPackage, targetMetadata.getPackage());
    assertNotNull(targetMetadata.getAttribute(SOURCE));
  }

  /**
   * New entities in the source should be added to the target when a new mapping to the same target
   * is performed.
   */
  @Test
  @SuppressWarnings("unchecked")
  void testApplyMappingsAdd() {
    String entityTypeId = "addEntity";
    @SuppressWarnings("unchecked")
    Repository<Entity> addEntityRepo = mock(Repository.class);
    when(addEntityRepo.getName()).thenReturn(entityTypeId);
    EntityType targetMeta = entityTypeFactory.create(TARGET_HOP_ENTITY).setPackage(package_);
    targetMeta.addAttribute(attrMetaFactory.create().setName("identifier"), ROLE_ID);
    targetMeta.addAttribute(
        attrMetaFactory.create().setName("height").setDataType(DECIMAL).setNillable(false));
    targetMeta.addAttribute(attrMetaFactory.create().setName("source"));

    when(addEntityRepo.getEntityType()).thenReturn(targetMeta);
    when(metaDataService.createRepository(argThat(obj -> obj.getId().equals(entityTypeId))))
        .thenReturn(addEntityRepo);

    // add an entity to the source
    List<Entity> sourceGeneEntities = newArrayList();
    List<Entity> expectedEntities = newArrayList();
    createEntities(targetMeta, sourceGeneEntities, expectedEntities);

    Mockito.doAnswer(
            invocationOnMock -> {
              @SuppressWarnings("unchecked")
              Consumer<List<Entity>> consumer =
                  (Consumer<List<Entity>>) invocationOnMock.<Consumer>getArgument(0);

              consumer.accept(sourceGeneEntities);
              return null;
            })
        .when(geneRepo)
        .forEachBatched(ArgumentMatchers.any(Consumer.class), eq(MAPPING_BATCH_SIZE));

    // create project and apply mappings once
    createMappingProjectWithMappings();
    Entity mappedEntity = mock(Entity.class);
    when(entityManager.create(targetMeta, EntityManager.CreationMode.POPULATE))
        .thenReturn(mappedEntity);

    // apply mapping again
    String packageId = "packageId";
    Package aPackage = mock(Package.class);
    when(metaDataService.getPackage(packageId)).thenReturn(Optional.of(aPackage));
    assertEquals(
        4,
        mappingService.applyMappings("TestRun", entityTypeId, true, packageId, "label", progress));

    Mockito.verify(geneRepo)
        .forEachBatched(ArgumentMatchers.any(Consumer.class), ArgumentMatchers.any(Integer.class));

    ArgumentCaptor<EntityType> entityTypeCaptor = ArgumentCaptor.forClass(EntityType.class);
    Mockito.verify(permissionSystemService)
        .giveUserWriteMetaPermissions(entityTypeCaptor.capture());
    assertEquals(entityTypeId, entityTypeCaptor.getValue().getId());
    Mockito.verify(progress).setProgressMax(ArgumentMatchers.anyInt());
    Mockito.verify(progress).progress(0, "Checking target repository [addEntity]...");
    Mockito.verify(progress).status("Applying mappings to repository [HopEntity]");
    Mockito.verify(progress).status("Mapping source [Genes]...");
    Mockito.verify(progress).increment(1);
    Mockito.verify(progress).status("Mapped 4 [Genes] entities.");
    Mockito.verify(progress).status("Done applying mappings to repository [HopEntity]");
    Mockito.verifyNoMoreInteractions(progress);
  }

  /** Applying a mapping multiple times to the same target should update the existing entities. */
  @SuppressWarnings("unchecked")
  @Test
  void testApplyMappingsUpdate() {
    String entityTypeId = "updateEntity";

    @SuppressWarnings("unchecked")
    Repository<Entity> updateEntityRepo = mock(Repository.class);
    when(updateEntityRepo.getName()).thenReturn(entityTypeId);
    EntityType targetMeta = entityTypeFactory.create(TARGET_HOP_ENTITY).setPackage(package_);
    targetMeta.addAttribute(attrMetaFactory.create().setName("identifier"), ROLE_ID);
    targetMeta.addAttribute(
        attrMetaFactory.create().setName("height").setDataType(DECIMAL).setNillable(false));
    targetMeta.addAttribute(attrMetaFactory.create().setName("source"));
    when(dataService.hasRepository(entityTypeId)).thenReturn(true);
    when(dataService.getRepository(entityTypeId)).thenReturn(updateEntityRepo);
    when(metaDataService.getEntityType(entityTypeId)).thenReturn(Optional.of(targetMeta));

    when(updateEntityRepo.getEntityType()).thenReturn(targetMeta);
    when(metaDataService.createRepository(argThat(obj -> obj.getLabel().equals(entityTypeId))))
        .thenReturn(updateEntityRepo);

    // add an entity to the source
    List<Entity> sourceGeneEntities = newArrayList();
    List<Entity> expectedEntities = newArrayList();
    createEntities(targetMeta, sourceGeneEntities, expectedEntities);

    when(updateEntityRepo.count()).thenReturn(4L);
    when(updateEntityRepo.findAll(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(expectedEntities.stream());

    Mockito.doAnswer(
            invocationOnMock -> {
              @SuppressWarnings("unchecked")
              Consumer<List<Entity>> consumer =
                  (Consumer<List<Entity>>) invocationOnMock.<Consumer>getArgument(0);
              consumer.accept(sourceGeneEntities);
              return null;
            })
        .when(geneRepo)
        .forEachBatched(ArgumentMatchers.any(Consumer.class), eq(MAPPING_BATCH_SIZE));

    // create mapping project and apply mappings once
    createMappingProjectWithMappings();
    when(entityManager.create(targetMeta, EntityManager.CreationMode.POPULATE))
        .thenAnswer(invocation -> new DynamicEntity(targetMeta));

    // apply mapping again
    assertEquals(
        4,
        mappingService.applyMappings(
            "TestRun", entityTypeId, false, "packageId", "label", progress));

    Mockito.verify(geneRepo)
        .forEachBatched(ArgumentMatchers.any(Consumer.class), ArgumentMatchers.any(Integer.class));

    Mockito.verify(updateEntityRepo).upsertBatch(batchCaptor.capture());
    assertTrue(EntityUtils.equalsEntities(batchCaptor.getValue(), expectedEntities));

    Mockito.verify(progress).status("Applying mappings to repository [HopEntity]");
    Mockito.verify(progress).status("Mapping source [Genes]...");
    Mockito.verify(progress).increment(1);
    Mockito.verify(progress).status("Mapped 4 [Genes] entities.");
    Mockito.verify(progress).status("Done applying mappings to repository [HopEntity]");
    Mockito.verifyZeroInteractions(permissionSystemService);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testApplyMappingsToRepoAdd() {
    Repository<Entity> targetRepo = mock(Repository.class);
    Repository<Entity> sourceRepo = mock(Repository.class);
    EntityMapping sourceMapping = mock(EntityMapping.class);
    when(sourceMapping.getLabel()).thenReturn("sourceMappingLabel");
    when(sourceMapping.getName()).thenReturn("sourceMappingID");

    EntityType sourceEntityType = mock(EntityType.class);
    when(sourceEntityType.getLabel()).thenReturn("test");
    when(sourceRepo.getEntityType()).thenReturn(sourceEntityType);
    when(dataService.getRepository("sourceMappingID")).thenReturn(sourceRepo);
    when(targetRepo.count()).thenReturn(0L);

    EntityType targetEntityType = mock(EntityType.class);
    when(targetEntityType.getId()).thenReturn("targetEntityType");
    when(targetEntityType.getAtomicAttributes()).thenReturn(newArrayList());
    Attribute targetID = mock(Attribute.class);
    when(targetID.getName()).thenReturn("targetID");
    when(targetEntityType.getIdAttribute()).thenReturn(targetID);
    when(targetRepo.getEntityType()).thenReturn(targetEntityType);

    List<Entity> batch = Lists.newArrayList(mock(Entity.class));
    Mockito.doAnswer(
            invocationOnMock -> {
              Consumer<List<Entity>> consumer =
                  (Consumer<List<Entity>>) invocationOnMock.<Consumer>getArgument(0);

              consumer.accept(batch);
              consumer.accept(batch);
              consumer.accept(batch);
              return null;
            })
        .when(sourceRepo)
        .forEachBatched(ArgumentMatchers.any(Consumer.class), eq(MAPPING_BATCH_SIZE));

    mappingService.applyMappingToRepo(sourceMapping, targetRepo, progress, 3);

    Mockito.verify(targetRepo, Mockito.times(3)).add(ArgumentMatchers.any(Stream.class));
    Mockito.verify(progress, Mockito.times(3)).increment(1);
    Mockito.verify(progress).status("Mapping source [sourceMappingLabel]...");
    Mockito.verify(progress).status("Mapped 3 [sourceMappingLabel] entities.");
    Mockito.verifyNoMoreInteractions(progress);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testApplyMappingsToRepoUpsert() {
    Repository<Entity> targetRepo = mock(Repository.class);
    Repository<Entity> sourceRepo = mock(Repository.class);
    EntityMapping sourceMapping = mock(EntityMapping.class);
    when(sourceMapping.getLabel()).thenReturn("sourceMappingLabel");
    when(sourceMapping.getName()).thenReturn("sourceMappingID");

    when(dataService.getRepository("sourceMappingID")).thenReturn(sourceRepo);
    when(targetRepo.count()).thenReturn(3L);

    EntityType targetEntityType = mock(EntityType.class);
    when(targetEntityType.getId()).thenReturn("targetEntityType");
    when(targetEntityType.getAtomicAttributes()).thenReturn(newArrayList());
    Attribute targetID = mock(Attribute.class);
    when(targetID.getName()).thenReturn("targetID");
    when(targetEntityType.getIdAttribute()).thenReturn(targetID);
    when(targetRepo.getEntityType()).thenReturn(targetEntityType);

    List<Entity> batch = newArrayList(mock(Entity.class), mock(Entity.class));

    Mockito.doAnswer(
            invocationOnMock -> {
              Consumer<List<Entity>> consumer =
                  (Consumer<List<Entity>>) invocationOnMock.<Consumer>getArgument(0);
              consumer.accept(batch);
              consumer.accept(batch);
              return null;
            })
        .when(sourceRepo)
        .forEachBatched(ArgumentMatchers.any(Consumer.class), eq(MAPPING_BATCH_SIZE));

    mappingService.applyMappingToRepo(sourceMapping, targetRepo, progress, 3);

    Mockito.verify(targetRepo, Mockito.times(2)).upsertBatch(ArgumentMatchers.any(List.class));
    Mockito.verify(progress, Mockito.times(2)).increment(1);
    Mockito.verify(progress).status("Mapping source [sourceMappingLabel]...");
    Mockito.verify(progress).status("Mapped 4 [sourceMappingLabel] entities.");
    Mockito.verifyNoMoreInteractions(progress);
  }

  @Test
  void testIncompatibleMetaDataUnknownAttribute() {
    String targetRepositoryName = "targetRepository";

    @SuppressWarnings("unchecked")
    Repository<Entity> targetRepository = mock(Repository.class);
    EntityType targetRepositoryMetaData = entityTypeFactory.create(targetRepositoryName);
    targetRepositoryMetaData.addAttribute(
        attrMetaFactory.create().setName("ID").setDataType(STRING), ROLE_ID);
    targetRepositoryMetaData.addAttribute(
        attrMetaFactory.create().setName("COUNTRY").setDataType(STRING));
    targetRepositoryMetaData.setPackage(package_);

    when(dataService.hasRepository(targetRepositoryName)).thenReturn(true);
    when(dataService.getRepository(targetRepositoryName)).thenReturn(targetRepository);
    when(metaDataService.getEntityType(targetRepositoryName))
        .thenReturn(Optional.of(targetRepositoryMetaData));
    when(targetRepository.getEntityType()).thenReturn(targetRepositoryMetaData);

    EntityType mappingTargetMetaData = entityTypeFactory.create("mapping_target");
    mappingTargetMetaData.addAttribute(
        attrMetaFactory.create().setName("ID").setDataType(STRING), ROLE_ID);
    mappingTargetMetaData.addAttribute(
        attrMetaFactory.create().setName("COUNTRY_1").setDataType(STRING));
    mappingTargetMetaData.setPackage(package_);

    MappingTarget mappingTarget = new MappingTarget(mappingTargetMetaData);
    MappingProject mappingProject = mock(MappingProject.class);
    when(mappingProject.getMappingTargets()).thenReturn(newArrayList(mappingTarget));
    when(mappingProjectRepo.getMappingProject("TestRun")).thenReturn(mappingProject);
    Exception exception =
        assertThrows(
            MolgenisDataException.class,
            () ->
                mappingService.applyMappings(
                    "TestRun", targetRepositoryName, false, "packageId", "label", progress));
    assertThat(exception.getMessage())
        .containsPattern("Target repository does not contain the following attribute: COUNTRY_1");
  }

  @Test
  void testIncompatibleMetaDataDifferentType() {
    String targetRepositoryName = "target_repository";

    @SuppressWarnings("unchecked")
    Repository<Entity> targetRepository = mock(Repository.class);
    EntityType targetRepositoryMetaData = entityTypeFactory.create(targetRepositoryName);
    targetRepositoryMetaData.addAttribute(
        attrMetaFactory.create().setName("ID").setDataType(STRING), ROLE_ID);
    targetRepositoryMetaData.addAttribute(
        attrMetaFactory.create().setName("COUNTRY").setDataType(STRING));
    targetRepositoryMetaData.setPackage(package_);

    when(dataService.hasRepository(targetRepositoryName)).thenReturn(true);
    when(dataService.getRepository(targetRepositoryName)).thenReturn(targetRepository);
    when(targetRepository.getEntityType()).thenReturn(targetRepositoryMetaData);
    when(metaDataService.getEntityType(targetRepositoryName))
        .thenReturn(Optional.of(targetRepositoryMetaData));

    when(targetRepository.getEntityType()).thenReturn(targetRepositoryMetaData);

    EntityType mappingTargetMetaData = entityTypeFactory.create("mapping_target");
    mappingTargetMetaData.addAttribute(
        attrMetaFactory.create().setName("ID").setDataType(STRING), ROLE_ID);
    mappingTargetMetaData.addAttribute(
        attrMetaFactory.create().setName("COUNTRY").setDataType(INT));
    mappingTargetMetaData.setPackage(package_);

    MappingTarget mappingTarget = new MappingTarget(mappingTargetMetaData);
    MappingProject mappingProject = mock(MappingProject.class);
    when(mappingProject.getMappingTargets()).thenReturn(newArrayList(mappingTarget));
    when(mappingProjectRepo.getMappingProject("TestRun")).thenReturn(mappingProject);
    Exception exception =
        assertThrows(
            MolgenisDataException.class,
            () ->
                mappingService.applyMappings(
                    "TestRun", targetRepositoryName, false, "packageId", "label", progress));
    assertThat(exception.getMessage())
        .containsPattern(
            "attribute COUNTRY in the mapping target is type INT while attribute "
                + "COUNTRY in the target repository is type STRING. Please make sure the types are the same");
  }

  @Test
  void testIncompatibleMetaDataDifferentRefEntity() {
    String targetRepositoryName = "target_repository";
    String targetRepositoryRefEntityName = "target_repository_ref";
    String mappingTargetRefEntityName = "mapping_target_ref";

    EntityType targetRefEntity = entityTypeFactory.create(targetRepositoryRefEntityName);

    @SuppressWarnings("unchecked")
    Repository<Entity> targetRepository = mock(Repository.class);
    EntityType targetRepositoryMetaData = entityTypeFactory.create(targetRepositoryName);
    targetRepositoryMetaData.addAttribute(
        attrMetaFactory.create().setName("ID").setDataType(STRING), ROLE_ID);
    targetRepositoryMetaData.addAttribute(
        attrMetaFactory
            .create()
            .setName("COUNTRY")
            .setDataType(XREF)
            .setRefEntity(targetRefEntity));
    targetRepositoryMetaData.setPackage(package_);

    when(dataService.hasRepository(targetRepositoryName)).thenReturn(true);
    when(dataService.getRepository(targetRepositoryName)).thenReturn(targetRepository);
    when(targetRepository.getEntityType()).thenReturn(targetRepositoryMetaData);
    when(metaDataService.getEntityType(targetRepositoryName))
        .thenReturn(Optional.of(targetRepositoryMetaData));

    EntityType mappingTargetRefEntity = entityTypeFactory.create(mappingTargetRefEntityName);

    EntityType mappingTargetMetaData = entityTypeFactory.create("mapping_target");
    mappingTargetMetaData.addAttribute(
        attrMetaFactory.create().setName("ID").setDataType(STRING), ROLE_ID);
    mappingTargetMetaData.addAttribute(
        attrMetaFactory
            .create()
            .setName("COUNTRY")
            .setDataType(XREF)
            .setRefEntity(mappingTargetRefEntity));
    mappingTargetMetaData.setPackage(package_);

    when(metaDataService.createRepository(mappingTargetMetaData)).thenReturn(targetRepository);

    MappingTarget mappingTarget = new MappingTarget(mappingTargetMetaData);

    MappingProject mappingProject = mock(MappingProject.class);
    when(mappingProject.getMappingTargets()).thenReturn(newArrayList(mappingTarget));
    when(mappingProjectRepo.getMappingProject("TestRun")).thenReturn(mappingProject);
    Exception exception =
        assertThrows(
            MolgenisDataException.class,
            () ->
                mappingService.applyMappings(
                    "TestRun", targetRepositoryName, false, "packageId", "label", progress));
    assertThat(exception.getMessage())
        .containsPattern(
            "In the mapping target, attribute COUNTRY of type XREF has "
                + "reference entity mapping_target_ref while in the target repository attribute COUNTRY of type XREF has reference entity target_repository_ref. "
                + "Please make sure the reference entities of your mapping target are pointing towards the same reference entities as your target repository");
  }

  @Test
  void testGetCompatibleEntityTypes() {
    when(metaDataService.getEntityTypes()).thenReturn(Stream.of(hopMetaData, geneMetaData));
    Set<Entity> compatibleEntityTypes =
        mappingService.getCompatibleEntityTypes(hopMetaData).collect(toSet());
    assertEquals(newHashSet(hopMetaData), compatibleEntityTypes);
  }

  @Test
  void testMaxProgressOneSourceOneBatch() {
    MappingTarget mappingTarget = mock(MappingTarget.class);
    EntityMapping entityMapping = getMockEntityMapping("a", MAPPING_BATCH_SIZE - 1);
    List<EntityMapping> mappings = singletonList(entityMapping);
    when(mappingTarget.getEntityMappings()).thenReturn(mappings);

    assertEquals(1, mappingService.calculateMaxProgress(mappingTarget));
  }

  @Test
  void testMaxProgressOneSourceMultipleBatches() {
    MappingTarget mappingTarget = mock(MappingTarget.class);
    EntityMapping entityMapping = getMockEntityMapping("a", (3 * MAPPING_BATCH_SIZE) + 1);
    List<EntityMapping> mappings = singletonList(entityMapping);
    when(mappingTarget.getEntityMappings()).thenReturn(mappings);

    assertEquals(4, mappingService.calculateMaxProgress(mappingTarget));
  }

  @Test
  void testMaxProgressOneSourceMultipleBatchesSelfReferencing() {
    MappingTarget mappingTarget = mock(MappingTarget.class);
    EntityMapping entityMapping = getMockEntityMapping("a", (3 * MAPPING_BATCH_SIZE) + 1);
    when(mappingTarget.hasSelfReferences()).thenReturn(true);
    List<EntityMapping> mappings = singletonList(entityMapping);
    when(mappingTarget.getEntityMappings()).thenReturn(mappings);

    assertEquals(8, mappingService.calculateMaxProgress(mappingTarget));
  }

  @Test
  void testMaxProgressMultipleSourcesSelfReferencing() {
    MappingTarget mappingTarget = mock(MappingTarget.class);
    EntityMapping mapping1 = getMockEntityMapping("a", MAPPING_BATCH_SIZE);
    EntityMapping mapping2 = getMockEntityMapping("b", MAPPING_BATCH_SIZE + 1);
    List<EntityMapping> mappings = newArrayList(mapping1, mapping2);
    when(mappingTarget.hasSelfReferences()).thenReturn(true);
    when(mappingTarget.getEntityMappings()).thenReturn(mappings);

    assertEquals(6, mappingService.calculateMaxProgress(mappingTarget));
  }

  @Test
  void testMaxProgressMultipleSources() {
    MappingTarget mappingTarget = mock(MappingTarget.class);
    EntityMapping mapping1 = getMockEntityMapping("a", MAPPING_BATCH_SIZE);
    EntityMapping mapping2 = getMockEntityMapping("b", MAPPING_BATCH_SIZE + 1);
    List<EntityMapping> mappings = newArrayList(mapping1, mapping2);
    when(mappingTarget.getEntityMappings()).thenReturn(mappings);

    assertEquals(3, mappingService.calculateMaxProgress(mappingTarget));
  }

  private EntityMapping getMockEntityMapping(String id, long sourceRows) {
    EntityMapping entityMapping = mock(EntityMapping.class);
    EntityType sourceEntityType = mock(EntityType.class);
    when(entityMapping.getSourceEntityType()).thenReturn(sourceEntityType);
    when(sourceEntityType.getId()).thenReturn(id);
    when(dataService.count(id)).thenReturn(sourceRows);
    return entityMapping;
  }

  private void createEntities(
      EntityType targetMeta, List<Entity> sourceGeneEntities, List<Entity> expectedEntities) {
    for (int i = 0; i < 4; ++i) {
      Entity geneEntity = new DynamicEntity(geneMetaData);
      geneEntity.set("id", String.valueOf(i));
      geneEntity.set("length", i * 2d);
      sourceGeneEntities.add(geneEntity);

      when(algorithmService.apply(
              argThat(obj -> obj != null && obj.getAlgorithm().equals("$('id').value()")),
              eq(geneEntity),
              eq(3)))
          .thenReturn(geneEntity.getString("id"));

      when(algorithmService.apply(
              argThat(obj -> obj != null && obj.getAlgorithm().equals("$('length').value()")),
              eq(geneEntity),
              eq(3)))
          .thenReturn(geneEntity.getDouble("length"));

      Entity expectedEntity = new DynamicEntity(targetMeta);
      expectedEntity.set("identifier", String.valueOf(i));
      expectedEntity.set("height", i * 2d);
      expectedEntity.set("source", geneMetaData.getId());
      expectedEntities.add(expectedEntity);
    }
  }

  private MappingTarget getManualMappingTarget(
      String identifier, Collection<EntityMapping> entityMappings) {
    return new MappingTarget(identifier, hopMetaData, entityMappings);
  }

  private MappingProject createMappingProjectWithMappings() {
    MappingProject mappingProject =
        mappingService.addMappingProject("TestRun", hopMetaData.getId(), 3);
    MappingTarget target = mappingProject.getMappingTarget(hopMetaData.getId());

    when(mappingProjectRepo.getMappingProject("TestRun")).thenReturn(mappingProject);

    EntityMapping mapping = target.addSource(geneMetaData);

    AttributeMapping idMapping = mapping.addAttributeMapping("identifier");
    idMapping.setAlgorithm("$('id').value()");
    AttributeMapping attrMapping = mapping.addAttributeMapping("height");
    attrMapping.setAlgorithm("$('length').value()");

    return mappingProject;
  }

  private MappingProject getManualMappingProject(
      String identifier, String projectName, int depth, MappingTarget mappingTarget) {
    return new MappingProject(identifier, projectName, depth, newArrayList(mappingTarget));
  }

  @Configuration
  @Import(UserTestConfig.class)
  static class Config {

    @Bean
    EntityManager entityManager() {
      return mock(EntityManager.class);
    }

    @Bean
    AlgorithmService algorithmService() {
      return mock(AlgorithmService.class);
    }

    @Bean
    JsMagmaScriptEvaluator jsMagmaScriptEvaluator() {
      return mock(JsMagmaScriptEvaluator.class);
    }

    @Bean
    MappingProjectRepository mappingProjectRepository() {
      return mock(MappingProjectRepository.class);
    }

    @Bean
    PermissionSystemService permissionSystemService() {
      return mock(PermissionSystemService.class);
    }
  }
}
