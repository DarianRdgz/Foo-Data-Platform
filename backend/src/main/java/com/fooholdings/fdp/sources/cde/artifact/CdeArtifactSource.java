package com.fooholdings.fdp.sources.cde.artifact;

import java.util.List;

public interface CdeArtifactSource {
    List<CdeArtifact> discoverPendingArtifacts();
}
