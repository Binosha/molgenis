package org.molgenis.data.meta;

import com.google.common.collect.Sets;
import com.google.common.collect.TreeTraverser;
import org.molgenis.auth.GroupAuthority;
import org.molgenis.auth.UserAuthority;
import org.molgenis.data.*;
import org.molgenis.data.meta.model.Attribute;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.data.meta.system.SystemEntityTypeRegistry;
import org.molgenis.data.support.QueryImpl;
import org.molgenis.security.core.MolgenisPermissionService;
import org.molgenis.security.core.Permission;
import org.molgenis.security.core.utils.SecurityUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.molgenis.auth.AuthorityMetaData.ROLE;
import static org.molgenis.auth.GroupAuthorityMetaData.GROUP_AUTHORITY;
import static org.molgenis.auth.UserAuthorityMetaData.USER_AUTHORITY;
import static org.molgenis.data.meta.model.AttributeMetadata.ATTRIBUTE_META_DATA;
import static org.molgenis.data.meta.model.AttributeMetadata.NAME;
import static org.molgenis.data.meta.model.EntityTypeMetadata.*;
import static org.molgenis.security.core.Permission.COUNT;
import static org.molgenis.security.core.Permission.READ;
import static org.molgenis.security.core.utils.SecurityUtils.currentUserIsSu;
import static org.molgenis.security.core.utils.SecurityUtils.currentUserisSystem;
import static org.molgenis.util.SecurityDecoratorUtils.validatePermission;

/**
 * Decorator for the entity meta data repository:
 * - filters requested entities based on the permissions of the current user.
 * - applies updates to the repository collection for entity meta data adds/updates/deletes
 * <p>
 * TODO replace permission based entity filtering with generic row-level security once available
 */
public class EntityTypeRepositoryDecorator implements Repository<EntityType>
{
	private final Repository<EntityType> decoratedRepo;
	private final DataService dataService;
	private final SystemEntityTypeRegistry systemEntityTypeRegistry;
	private final MolgenisPermissionService permissionService;

	public EntityTypeRepositoryDecorator(Repository<EntityType> decoratedRepo, DataService dataService,
			SystemEntityTypeRegistry systemEntityTypeRegistry, MolgenisPermissionService permissionService)
	{
		this.decoratedRepo = requireNonNull(decoratedRepo);
		this.dataService = requireNonNull(dataService);
		this.systemEntityTypeRegistry = requireNonNull(systemEntityTypeRegistry);
		this.permissionService = requireNonNull(permissionService);
	}

	@Override
	public Set<RepositoryCapability> getCapabilities()
	{
		return decoratedRepo.getCapabilities();
	}

	@Override
	public void close() throws IOException
	{
		decoratedRepo.close();
	}

	@Override
	public String getName()
	{
		return decoratedRepo.getName();
	}

	@Override
	public Set<QueryRule.Operator> getQueryOperators()
	{
		return decoratedRepo.getQueryOperators();
	}

	public EntityType getEntityType()
	{
		return decoratedRepo.getEntityType();
	}

	@Override
	public long count()
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			return decoratedRepo.count();
		}
		else
		{
			Stream<EntityType> EntityTypes = StreamSupport.stream(decoratedRepo.spliterator(), false);
			return filterCountPermission(EntityTypes).count();
		}
	}

	@Override
	public Query<EntityType> query()
	{
		return decoratedRepo.query();
	}

	@Override
	public long count(Query<EntityType> q)
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			return decoratedRepo.count(q);
		}
		else
		{
			// ignore query offset and page size
			Query<EntityType> qWithoutLimitOffset = new QueryImpl<>(q);
			qWithoutLimitOffset.offset(0).pageSize(Integer.MAX_VALUE);
			Stream<EntityType> EntityTypes = decoratedRepo.findAll(qWithoutLimitOffset);
			return filterCountPermission(EntityTypes).count();
		}
	}

	@Override
	public Stream<EntityType> findAll(Query<EntityType> q)
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			return decoratedRepo.findAll(q);
		}
		else
		{
			Query<EntityType> qWithoutLimitOffset = new QueryImpl<>(q);
			qWithoutLimitOffset.offset(0).pageSize(Integer.MAX_VALUE);
			Stream<EntityType> EntityTypes = decoratedRepo.findAll(qWithoutLimitOffset);
			Stream<EntityType> filteredEntityTypes = filterReadPermission(EntityTypes);
			if (q.getOffset() > 0)
			{
				filteredEntityTypes = filteredEntityTypes.skip(q.getOffset());
			}
			if (q.getPageSize() > 0)
			{
				filteredEntityTypes = filteredEntityTypes.limit(q.getPageSize());
			}
			return filteredEntityTypes;
		}
	}

	@Override
	public Iterator<EntityType> iterator()
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			return decoratedRepo.iterator();
		}
		else
		{
			Stream<EntityType> EntityTypeStream = StreamSupport.stream(decoratedRepo.spliterator(), false);
			return filterReadPermission(EntityTypeStream).iterator();
		}
	}

	@Override
	public void forEachBatched(Fetch fetch, Consumer<List<EntityType>> consumer, int batchSize)
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			decoratedRepo.forEachBatched(fetch, consumer, batchSize);
		}
		else
		{
			FilteredConsumer filteredConsumer = new FilteredConsumer(consumer, permissionService);
			decoratedRepo.forEachBatched(fetch, filteredConsumer::filter, batchSize);
		}
	}

	@Override
	public EntityType findOne(Query<EntityType> q)
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			return decoratedRepo.findOne(q);
		}
		else
		{
			// ignore query offset and page size
			return filterReadPermission(decoratedRepo.findOne(q));
		}
	}

	@Override
	public EntityType findOneById(Object id)
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			return decoratedRepo.findOneById(id);
		}
		else
		{
			return filterReadPermission(decoratedRepo.findOneById(id));
		}
	}

	@Override
	public EntityType findOneById(Object id, Fetch fetch)
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			return decoratedRepo.findOneById(id, fetch);
		}
		else
		{
			return filterReadPermission(decoratedRepo.findOneById(id, fetch));
		}
	}

	@Override
	public Stream<EntityType> findAll(Stream<Object> ids)
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			return decoratedRepo.findAll(ids);
		}
		else
		{
			return filterReadPermission(decoratedRepo.findAll(ids));
		}
	}

	@Override
	public Stream<EntityType> findAll(Stream<Object> ids, Fetch fetch)
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			return decoratedRepo.findAll(ids, fetch);
		}
		else
		{
			return filterReadPermission(decoratedRepo.findAll(ids, fetch));
		}
	}

	@Override
	public AggregateResult aggregate(AggregateQuery aggregateQuery)
	{
		if (currentUserIsSu() || currentUserisSystem())
		{
			return decoratedRepo.aggregate(aggregateQuery);
		}
		else
		{
			throw new MolgenisDataAccessException(format("Aggregation on entity [%s] not allowed", getName()));
		}
	}

	@Override
	public void update(EntityType entity)
	{
		updateEntity(entity);
	}

	@Override
	public void update(Stream<EntityType> entities)
	{
		entities.forEach(this::updateEntity);
	}

	@Override
	public void delete(EntityType entity)
	{
		deleteEntityType(entity);
	}

	@Override
	public void delete(Stream<EntityType> entities)
	{
		entities.forEach(this::deleteEntityType);
	}

	@Override
	public void deleteById(Object id)
	{
		EntityType entityType = findOneById(id);
		if (entityType == null)
		{
			throw new UnknownEntityException(
					format("Unknown entity meta data [%s] with id [%s]", getName(), id.toString()));
		}
		deleteEntityType(entityType);
	}

	@Override
	public void deleteAll(Stream<Object> ids)
	{
		findAll(ids).forEach(this::deleteEntityType);
	}

	@Override
	public void deleteAll()
	{
		iterator().forEachRemaining(this::deleteEntityType);
	}

	@Override
	public void add(EntityType entity)
	{
		addEntityType(entity);
	}

	@Override
	public Integer add(Stream<EntityType> entities)
	{
		AtomicInteger count = new AtomicInteger();
		entities.filter(entity ->
		{
			count.incrementAndGet();
			return true;
		}).forEach(this::addEntityType);
		return count.get();
	}

	private void addEntityType(EntityType entityType)
	{
		validatePermission(entityType.getName(), Permission.WRITEMETA);

		// add row to entities table
		decoratedRepo.add(entityType);
		if (!entityType.isAbstract() && !dataService.getMeta().isMetaEntityType(entityType))
		{
			RepositoryCollection repoCollection = dataService.getMeta().getBackend(entityType.getBackend());
			if (repoCollection == null)
			{
				throw new MolgenisDataException(format("Unknown backend [%s]", entityType.getBackend()));
			}
			repoCollection.createRepository(entityType);
		}
	}

	private void updateEntity(EntityType entityType)
	{
		validateUpdateAllowed(entityType);

		EntityType existingEntityType = findOneById(entityType.getIdValue(),
				new Fetch().field(FULL_NAME).field(ATTRIBUTES, new Fetch().field(NAME)));
		if (existingEntityType == null)
		{
			throw new UnknownEntityException(format("Unknown entity meta data [%s] with id [%s]", getName(),
					entityType.getIdValue().toString()));
		}
		Map<String, Attribute> currentAttrMap = StreamSupport
				.stream(existingEntityType.getOwnAllAttributes().spliterator(), false)
				.collect(toMap(Attribute::getName, Function.identity()));
		Map<String, Attribute> updateAttrMap = StreamSupport
				.stream(entityType.getOwnAllAttributes().spliterator(), false)
				.collect(toMap(Attribute::getName, Function.identity()));

		// add attributes
		Set<String> addedAttrNames = Sets.difference(updateAttrMap.keySet(), currentAttrMap.keySet());
		if (!addedAttrNames.isEmpty())
		{
			String backend = entityType.getBackend();
			RepositoryCollection repoCollection = dataService.getMeta().getBackend(backend);
			addedAttrNames.stream().map(updateAttrMap::get).forEach(addedAttrEntity ->
			{
				repoCollection.addAttribute(existingEntityType, addedAttrEntity);

				if (entityType.getName().equals(ENTITY_TYPE_META_DATA))
				{
					// update system entity meta data
					systemEntityTypeRegistry.getSystemEntityType(ENTITY_TYPE_META_DATA).addAttribute(addedAttrEntity);
				}
			});
		}

		// update entity
		decoratedRepo.update(entityType);

		// remove attributes
		Set<String> deletedAttrNames = Sets.difference(currentAttrMap.keySet(), updateAttrMap.keySet());

		if (!deletedAttrNames.isEmpty())
		{
			String backend = entityType.getBackend();
			RepositoryCollection repoCollection = dataService.getMeta().getBackend(backend);
			deletedAttrNames.forEach(deletedAttrName ->
			{
				repoCollection.deleteAttribute(existingEntityType, currentAttrMap.get(deletedAttrName));

				if (entityType.getName().equals(ENTITY_TYPE_META_DATA))
				{
					// update system entity meta data
					systemEntityTypeRegistry.getSystemEntityType(ENTITY_TYPE_META_DATA)
							.removeAttribute(currentAttrMap.get(deletedAttrName));
				}
			});

			// delete attributes removed from entity meta data
			// assumption: the attribute is owned by this entity or a compound attribute owned by this entity
			dataService.deleteAll(ATTRIBUTE_META_DATA,
					deletedAttrNames.stream().map(currentAttrMap::get).map(Attribute::getIdentifier));
		}
	}

	/**
	 * Updating entityType meta data is allowed for non-system entities. For system entities updating entityType meta data is
	 * only allowed if the meta data defined in Java differs from the meta data stored in the database (in other words
	 * the Java code was updated).
	 *
	 * @param entityType entity meta data
	 */
	private void validateUpdateAllowed(EntityType entityType)
	{
		String entityName = entityType.getName();
		validatePermission(entityName, Permission.WRITEMETA);

		SystemEntityType systemEntityType = systemEntityTypeRegistry.getSystemEntityType(entityName);
		if (systemEntityType != null && !currentUserisSystem())
		{
			throw new MolgenisDataException(format("Updating system entity meta data [%s] is not allowed", entityName));
		}
	}

	private void deleteEntityType(EntityType entityType)
	{
		validateDeleteAllowed(entityType);

		// delete EntityType table
		if (!entityType.isAbstract())
		{
			deleteEntityRepository(entityType);
		}

		// delete EntityType permissions
		deleteEntityPermissions(entityType);

		// delete row from entities table
		decoratedRepo.delete(entityType);

		// delete rows from attributes table
		deleteEntityAttributes(entityType);
	}

	private void validateDeleteAllowed(EntityType entityType)
	{
		String entityName = entityType.getName();
		validatePermission(entityName, Permission.WRITEMETA);

		boolean isSystem = systemEntityTypeRegistry.hasSystemEntityType(entityName);
		if (isSystem)
		{
			throw new MolgenisDataException(format("Deleting system entity meta data [%s] is not allowed", entityName));
		}
	}

	private void deleteEntityAttributes(EntityType entityType)
	{
		Iterable<Attribute> rootAttrs = entityType.getOwnAttributes();
		Stream<Attribute> allAttrs = StreamSupport.stream(rootAttrs.spliterator(), false).flatMap(
				attrEntity -> StreamSupport
						.stream(new AttributeTreeTraverser().preOrderTraversal(attrEntity).spliterator(), false));
		dataService.delete(ATTRIBUTE_META_DATA, allAttrs);
	}

	private void deleteEntityRepository(EntityType entityType)
	{
		String backend = entityType.getBackend();
		dataService.getMeta().getBackend(backend).deleteRepository(entityType);
	}

	private void deleteEntityPermissions(EntityType entityType)
	{
		String entityName = entityType.getName();
		List<String> authorities = SecurityUtils.getEntityAuthorities(entityName);

		// User permissions
		List<UserAuthority> userPermissions = dataService.query(USER_AUTHORITY, UserAuthority.class)
				.in(ROLE, authorities).findAll().collect(toList());
		if (!userPermissions.isEmpty())
		{
			dataService.delete(USER_AUTHORITY, userPermissions.stream());
		}
		// Group permissions
		List<GroupAuthority> groupPermissions = dataService.query(GROUP_AUTHORITY, GroupAuthority.class)
				.in(ROLE, authorities).findAll().collect(toList());
		if (!groupPermissions.isEmpty())
		{
			dataService.delete(GROUP_AUTHORITY, groupPermissions.stream());
		}
	}

	private static class AttributeTreeTraverser extends TreeTraverser<Attribute>
	{

		@Override
		public Iterable<Attribute> children(@Nonnull Attribute attr)
		{
			return attr.getAttributeParts();
		}

	}

	private EntityType filterReadPermission(EntityType entityType)
	{
		return entityType != null ? filterReadPermission(Stream.of(entityType)).findFirst().orElse(null) : null;
	}

	private Stream<EntityType> filterReadPermission(Stream<EntityType> EntityTypeStream)
	{
		return filterPermission(EntityTypeStream, READ);
	}

	private Stream<EntityType> filterCountPermission(Stream<EntityType> EntityTypeStream)
	{
		return filterPermission(EntityTypeStream, COUNT);
	}

	private Stream<EntityType> filterPermission(Stream<EntityType> EntityTypeStream, Permission permission)
	{
		return EntityTypeStream
				.filter(entityType -> permissionService.hasPermissionOnEntity(entityType.getName(), permission));
	}

	private static class FilteredConsumer
	{
		private final Consumer<List<EntityType>> consumer;
		private final MolgenisPermissionService permissionService;

		FilteredConsumer(Consumer<List<EntityType>> consumer, MolgenisPermissionService permissionService)
		{
			this.consumer = requireNonNull(consumer);
			this.permissionService = requireNonNull(permissionService);
		}

		public void filter(List<EntityType> entityTypes)
		{
			List<EntityType> filteredEntityTypes = entityTypes.stream()
					.filter(entityType -> permissionService.hasPermissionOnEntity(entityType.getName(), READ))
					.collect(toList());
			consumer.accept(filteredEntityTypes);
		}
	}
}