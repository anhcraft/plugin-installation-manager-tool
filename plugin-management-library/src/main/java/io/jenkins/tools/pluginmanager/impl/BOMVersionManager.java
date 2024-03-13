package io.jenkins.tools.pluginmanager.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.util.VersionNumber;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BOMVersionManager {
  private final String bomVersion;
  private final RepositoryManager repositoryManager;
  private Map<String, VersionNumber> plugins;

  public BOMVersionManager(String bomVersion, RepositoryManager repositoryManager) {
    this.bomVersion = bomVersion;
    this.repositoryManager = repositoryManager;
  }

  private void fetch() {
    Map<String, VersionNumber> plugins = new HashMap<>();
    try {
      String coords = "io.jenkins.tools.bom:" + bomVersion + ":pom:LATEST";
      for (Dependency dependency : repositoryManager.requestArtifact(coords).getManagedDependencies()) {
        Artifact artifact = dependency.getArtifact();
        plugins.put(artifact.getGroupId()+":"+artifact.getArtifactId(), new VersionNumber(artifact.getVersion()));
      }
    } catch (ArtifactDescriptorException e) {
      System.out.println("Failed to fetch BOM: " + e.getMessage());
      e.printStackTrace();
    }
    this.plugins = Collections.unmodifiableMap(plugins);
  }

  public @Nullable VersionNumber getPluginVersion(@NonNull String groupId, @NonNull String artifactId) {
    if (plugins == null) {
      fetch();
    }
    return plugins.get(groupId+":"+artifactId);
  }
}
