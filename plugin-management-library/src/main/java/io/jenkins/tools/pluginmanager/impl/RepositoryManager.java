package io.jenkins.tools.pluginmanager.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class RepositoryManager {
  private static final File PATH_TO_LOCAL_REPO = Path.of(System.getProperty("user.home"), ".m2", "repository").toFile();
  private static final LocalRepository LOCAL_REPO = new LocalRepository(PATH_TO_LOCAL_REPO);
  private static final RemoteRepository JENKINS_REPO = new RemoteRepository.Builder("jenkins", "default", "https://repo.jenkins-ci.org/public").build();

  private final RepositorySystem repositorySystem;
  private final DefaultRepositorySystemSession session;
  private final List<RemoteRepository> remoteRepositories;

  public RepositoryManager() {
    this.repositorySystem = new RepositorySystemSupplier().get();
    this.session = setupSession();
    this.remoteRepositories = List.of(JENKINS_REPO);
  }

  private DefaultRepositorySystemSession setupSession() {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    // TODO custom local repository path
    session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, LOCAL_REPO));
    return session;
  }

  public @NonNull ArtifactDescriptorResult requestArtifact(@NonNull String coords) throws ArtifactDescriptorException {
    ArtifactDescriptorRequest pomRequest = new ArtifactDescriptorRequest();
    pomRequest.setArtifact(new DefaultArtifact(coords));
    pomRequest.setRepositories(remoteRepositories);
    return repositorySystem.readArtifactDescriptor(session, pomRequest);
  }
}
