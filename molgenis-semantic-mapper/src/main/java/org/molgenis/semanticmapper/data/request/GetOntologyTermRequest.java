package org.molgenis.semanticmapper.data.request;

import com.google.auto.value.AutoValue;
import org.molgenis.util.AutoGson;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@AutoValue
@AutoGson(autoValueClass = AutoValue_GetOntologyTermRequest.class)
@SuppressWarnings("squid:S1610") // Abstract classes without fields should be converted to interfaces
public abstract class GetOntologyTermRequest
{
	@NotBlank
	public abstract String getSearchTerm();

	@NotEmpty
	public abstract List<String> getOntologyIds();
}
