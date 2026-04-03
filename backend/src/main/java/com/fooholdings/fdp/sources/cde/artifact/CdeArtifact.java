package com.fooholdings.fdp.sources.cde.artifact;

import java.nio.file.Path;
import java.util.UUID;

public record CdeArtifact(
        UUID artifactId,
        String collectionCode,
        String stateCode,
        Integer artifactYear,
        String originalFilename,
        Path storagePath
) {}
