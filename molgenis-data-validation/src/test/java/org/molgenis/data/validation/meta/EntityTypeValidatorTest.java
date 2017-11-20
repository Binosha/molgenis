package org.molgenis.data.validation.meta;

import org.molgenis.data.DataService;
import org.molgenis.data.Query;
import org.molgenis.data.RepositoryCollection;
import org.molgenis.data.meta.MetaDataService;
import org.molgenis.data.meta.model.Attribute;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.data.meta.model.Package;
import org.molgenis.data.meta.system.SystemEntityTypeRegistry;
import org.molgenis.data.validation.ValidationException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.molgenis.data.meta.AttributeType.STRING;
import static org.molgenis.data.meta.AttributeType.XREF;
import static org.molgenis.data.meta.model.AttributeMetadata.ATTRIBUTE_META_DATA;
import static org.molgenis.data.meta.model.AttributeMetadata.CHILDREN;
import static org.molgenis.data.meta.model.EntityTypeMetadata.ATTRIBUTES;
import static org.molgenis.data.meta.model.EntityTypeMetadata.ENTITY_TYPE_META_DATA;
import static org.molgenis.data.system.model.RootSystemPackage.PACKAGE_SYSTEM;

public class EntityTypeValidatorTest
{
	private EntityTypeValidator entityTypeValidator;
	private DataService dataService;

	private EntityType entityType = when(mock(EntityType.class).getId()).thenReturn("entity").getMock();
	private Attribute idAttr;
	private Attribute labelAttr;
	private Query<EntityType> entityQ;
	private Query<Attribute> attrQ;
	private SystemEntityTypeRegistry systemEntityTypeRegistry;

	@SuppressWarnings("unchecked")
	@BeforeMethod
	public void setUpBeforeMethod()
	{
		dataService = mock(DataService.class);
		MetaDataService metaDataService = mock(MetaDataService.class);
		when(dataService.getMeta()).thenReturn(metaDataService);
		systemEntityTypeRegistry = mock(SystemEntityTypeRegistry.class);
		entityTypeValidator = new EntityTypeValidator(dataService, systemEntityTypeRegistry);

		String backendName = "backend";
		RepositoryCollection repoCollection = mock(RepositoryCollection.class);
		when(metaDataService.getBackend(backendName)).thenReturn(repoCollection);

		// valid entity meta
		entityType = when(mock(EntityType.class).getId()).thenReturn("entity").getMock();

		idAttr = when(mock(Attribute.class).getName()).thenReturn("idAttr").getMock();
		when(idAttr.getIdentifier()).thenReturn("#idAttr");
		when(idAttr.getDataType()).thenReturn(STRING);
		when(idAttr.isUnique()).thenReturn(true);
		when(idAttr.isNillable()).thenReturn(false);
		labelAttr = when(mock(Attribute.class).getName()).thenReturn("labelAttr").getMock();
		when(labelAttr.getIdentifier()).thenReturn("#labelAttr");
		when(labelAttr.getDataType()).thenReturn(STRING);

		entityQ = mock(Query.class);
		when(dataService.query(ENTITY_TYPE_META_DATA, EntityType.class)).thenReturn(entityQ);
		Query<EntityType> entityQ0 = mock(Query.class);
		Query<EntityType> entityQ1 = mock(Query.class);
		when(entityQ.eq(ATTRIBUTES, idAttr)).thenReturn(entityQ0);
		when(entityQ.eq(ATTRIBUTES, labelAttr)).thenReturn(entityQ1);
		when(entityQ0.findOne()).thenReturn(null);
		when(entityQ1.findOne()).thenReturn(null);

		attrQ = mock(Query.class);
		Query<Attribute> attrQ0 = mock(Query.class);
		Query<Attribute> attrQ1 = mock(Query.class);
		when(dataService.query(ATTRIBUTE_META_DATA, Attribute.class)).thenReturn(attrQ);
		when(attrQ.eq(CHILDREN, idAttr)).thenReturn(attrQ0);
		when(attrQ.eq(CHILDREN, labelAttr)).thenReturn(attrQ1);
		when(attrQ0.findOne()).thenReturn(null);
		when(attrQ1.findOne()).thenReturn(null);

		String packageName = "MyPackage";
		Package package_ = when(mock(Package.class).getId()).thenReturn(packageName).getMock();
		when(entityType.getPackage()).thenReturn(package_);
		String name = "MyEntityType";
		String label = "label";
		when(entityType.getId()).thenReturn(name);
		when(entityType.getLabel()).thenReturn(label);
		when(entityType.getOwnAllAttributes()).thenReturn(newArrayList(idAttr, labelAttr));
		when(entityType.getAllAttributes()).thenReturn(newArrayList(idAttr, labelAttr));
		when(entityType.getOwnIdAttribute()).thenReturn(idAttr);
		when(entityType.getOwnLabelAttribute()).thenReturn(labelAttr);
		when(entityType.getOwnLookupAttributes()).thenReturn(singletonList(labelAttr));
		when(entityType.isAbstract()).thenReturn(false);
		when(entityType.getExtends()).thenReturn(null);
		when(entityType.getBackend()).thenReturn(backendName);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:LABEL_NOT_EMPTY entityType:MyEntityType")
	public void testValidateLabelIsEmpty()
	{
		when(entityType.getLabel()).thenReturn("");
		entityTypeValidator.validate(entityType);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:LABEL_NOT_WHITESPACE_ONLY entityType:MyEntityType")
	public void testValidateLabelIsWhiteSpace()
	{
		when(entityType.getLabel()).thenReturn("  ");
		entityTypeValidator.validate(entityType);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:NAME entityType:logout")
	public void testValidateNameIsReservedKeyword() throws Exception
	{
		when(entityType.getId()).thenReturn("logout");
		entityTypeValidator.validate(entityType);
	}

	@Test
	public void testValidateAttributeOwnedBySameEntity()
	{
		@SuppressWarnings("unchecked")
		Query<EntityType> entityQ0 = mock(Query.class);
		@SuppressWarnings("unchecked")
		Query<EntityType> entityQ1 = mock(Query.class);
		when(entityQ.eq(ATTRIBUTES, idAttr)).thenReturn(entityQ0);
		when(entityQ.eq(ATTRIBUTES, labelAttr)).thenReturn(entityQ1);
		when(entityQ0.findOne()).thenReturn(null);
		when(entityQ1.findOne()).thenReturn(entityType); // same entity
		when(dataService.query(ATTRIBUTE_META_DATA, Attribute.class)).thenReturn(attrQ);
		when(attrQ.eq(CHILDREN, idAttr)).thenReturn(attrQ);
		when(attrQ.findOne()).thenReturn(null);
		entityTypeValidator.validate(entityType); // should not throw an exception
	}

	@Test
	public void testValidateAttributePartOwnedBySameEntity()
	{
		when(entityQ.eq(ATTRIBUTES, idAttr)).thenReturn(entityQ);
		when(entityQ.eq(ATTRIBUTES, labelAttr)).thenReturn(entityQ);
		when(entityQ.findOne()).thenReturn(null);
		@SuppressWarnings("unchecked")
		Query<Attribute> attrQ0 = mock(Query.class);
		@SuppressWarnings("unchecked")
		Query<Attribute> attrQ1 = mock(Query.class);
		when(dataService.query(ATTRIBUTE_META_DATA, Attribute.class)).thenReturn(attrQ);
		when(attrQ.eq(CHILDREN, idAttr)).thenReturn(attrQ0);
		when(attrQ.eq(CHILDREN, labelAttr)).thenReturn(attrQ1);
		when(attrQ0.findOne()).thenReturn(null);
		Attribute attrParent = when(mock(Attribute.class).getName()).thenReturn("attrParent").getMock();
		when(attrQ1.findOne()).thenReturn(attrParent);
		@SuppressWarnings("unchecked")
		Query<EntityType> entityQ0 = mock(Query.class);
		when(entityQ.eq(ATTRIBUTES, attrParent)).thenReturn(entityQ0);
		when(entityQ0.findOne()).thenReturn(entityType);
		entityTypeValidator.validate(entityType); // should not throw an exception
	}

	@Test
	public void testValidateAttributeNotOwnedByExtendedEntity()
	{
		EntityType extendsEntityType = when(mock(EntityType.class).getId()).thenReturn("entity").getMock();
		when(extendsEntityType.getAllAttributes()).thenReturn(emptyList());
		when(extendsEntityType.isAbstract()).thenReturn(true);
		when(entityType.getExtends()).thenReturn(extendsEntityType);
		entityTypeValidator.validate(entityType); // should not throw an exception
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:ATTRIBUTE_IN_PARENT entityType:MyEntityType")
	public void testValidateAttributeOwnedByExtendedEntity()
	{
		EntityType extendsEntityType = when(mock(EntityType.class).getId()).thenReturn("extendsEntity").getMock();
		when(extendsEntityType.getAllAttributes()).thenReturn(singletonList(idAttr));
		when(extendsEntityType.isAbstract()).thenReturn(true);
		when(entityType.getExtends()).thenReturn(extendsEntityType);
		entityTypeValidator.validate(entityType);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:ID_ATTRIBUTE_EXISTS entityType:MyEntityType")
	public void testValidateOwnIdAttributeInAttributes()
	{
		when(entityType.getOwnAllAttributes()).thenReturn(singletonList(labelAttr));
		entityTypeValidator.validate(entityType);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:ID_ATTRIBUTE_TYPE entityType:MyEntityType")
	public void testValidateOwnIdAttributeTypeAllowed()
	{
		when(idAttr.getDataType()).thenReturn(XREF);
		entityTypeValidator.validate(entityType);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:ID_ATTRIBUTE_UNIQUE entityType:MyEntityType")
	public void testValidateOwnIdAttributeUnique()
	{
		when(idAttr.isUnique()).thenReturn(false);
		entityTypeValidator.validate(entityType);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:ID_ATTRIBUTE_NOT_NULL entityType:MyEntityType")
	public void testValidateOwnIdAttributeNonNillable()
	{
		when(idAttr.isNillable()).thenReturn(true);
		entityTypeValidator.validate(entityType);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:ID_ATTRIBUTE_REQUIRED entityType:MyEntityType")
	public void testValidateOwnIdAttributeNullIdAttributeNull()
	{
		when(entityType.getOwnIdAttribute()).thenReturn(null);
		when(entityType.getIdAttribute()).thenReturn(null);
		entityTypeValidator.validate(entityType);
	}

	@Test
	public void testValidateOwnIdAttributeNullIdAttributeNullAbstract()
	{
		when(entityType.isAbstract()).thenReturn(true);
		when(entityType.getOwnIdAttribute()).thenReturn(null);
		when(entityType.getIdAttribute()).thenReturn(null);
		entityTypeValidator.validate(entityType); // valid
	}

	@Test
	public void testValidateOwnIdAttributeNullIdAttributeNotNull()
	{
		when(entityType.getOwnIdAttribute()).thenReturn(null);
		Attribute parentIdAttr = mock(Attribute.class);
		when(entityType.getIdAttribute()).thenReturn(parentIdAttr);
		entityTypeValidator.validate(entityType); // valid
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:LABEL_ATTRIBUTE_EXISTS entityType:MyEntityType")
	public void testValidateOwnLabelAttributeInAttributes()
	{
		when(entityType.getOwnAllAttributes()).thenReturn(singletonList(idAttr));
		entityTypeValidator.validate(entityType);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:LOOKUP_ATTRIBUTES_EXIST entityType:MyEntityType")
	public void testValidateOwnLookupAttributesInAttributes()
	{
		when(entityType.getOwnAllAttributes()).thenReturn(singletonList(idAttr));
		when(entityType.getOwnLabelAttribute()).thenReturn(null);
		entityTypeValidator.validate(entityType);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:BACKEND_EXISTS entityType:MyEntityType")
	public void testValidateBackend()
	{
		when(entityType.getBackend()).thenReturn("invalidBackend");
		entityTypeValidator.validate(entityType);
	}

	@Test
	public void testValidateExtendsFromAbstract()
	{
		EntityType extendsEntityType = mock(EntityType.class);
		when(extendsEntityType.getId()).thenReturn("abstractEntity");
		when(extendsEntityType.isAbstract()).thenReturn(true);
		when(extendsEntityType.getAllAttributes()).thenReturn(emptyList());
		when(entityType.getExtends()).thenReturn(extendsEntityType);
		entityTypeValidator.validate(entityType); // valid
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:EXTENDS_NOT_ABSTRACT entityType:MyEntityType")
	public void testValidateExtendsFromNonAbstract()
	{
		EntityType extendsEntityType = mock(EntityType.class);
		when(extendsEntityType.getId()).thenReturn("concreteEntity");
		when(extendsEntityType.isAbstract()).thenReturn(false);
		when(extendsEntityType.getAllAttributes()).thenReturn(emptyList());
		when(entityType.getExtends()).thenReturn(extendsEntityType);
		entityTypeValidator.validate(entityType);
	}

	@Test
	public void testValidateSystemPackageValid()
	{
		String packageName = PACKAGE_SYSTEM;
		Package rootSystemPackage = mock(Package.class);
		when(rootSystemPackage.getId()).thenReturn(packageName);

		String entityTypeId = "entity";
		when(entityType.getId()).thenReturn(entityTypeId);
		when(entityType.getPackage()).thenReturn(rootSystemPackage);

		when(systemEntityTypeRegistry.hasSystemEntityType(entityTypeId)).thenReturn(true);
		entityTypeValidator.validate(entityType); // valid
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:PACKAGE_NOT_SYSTEM entityType:myEntity")
	public void testValidateSystemPackageInvalid()
	{
		String packageName = PACKAGE_SYSTEM;
		Package rootSystemPackage = mock(Package.class);
		when(rootSystemPackage.getId()).thenReturn(packageName);

		String entityTypeId = "myEntity";
		when(entityType.getId()).thenReturn(entityTypeId);
		when(entityType.getPackage()).thenReturn(rootSystemPackage);

		when(systemEntityTypeRegistry.hasSystemEntityType(entityTypeId)).thenReturn(false);
		entityTypeValidator.validate(entityType);
	}

	@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "constraint:ATTRIBUTES_UNIQUE entityType:MyEntityType")
	public void testValidateAttributeWithDuplicateName()
	{
		when(entityType.getAllAttributes()).thenReturn(asList(idAttr, idAttr));
		entityTypeValidator.validate(entityType);
	}
}