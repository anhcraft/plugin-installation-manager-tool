package io.jenkins.tools.pluginmanager.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.artifact.SubArtifact;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

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

  public @Nullable File requestArtifactFile(@NonNull String coords, boolean offline) throws ArtifactResolutionException {
    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.setArtifact(new DefaultArtifact(coords));
    if (!offline)
      artifactRequest.setRepositories(remoteRepositories);
    ArtifactResult result = repositorySystem.resolveArtifact(session, artifactRequest);;
    if (result.getArtifact() != null)
      return result.getArtifact().getFile();
    else
      return null;
  }

  public void installPlugin(@NonNull File pluginFile) throws InstallationException, IOException {
    try (JarFile zip = new JarFile(pluginFile)) {
      Manifest manifest = zip.getManifest();
      String groupId = manifest.getMainAttributes().getValue("Group-Id");
      String artifactId = manifest.getMainAttributes().getValue("Artifact-Id");
      String version = manifest.getMainAttributes().getValue("Plugin-Version");

      JarEntry pomEntry = zip.getJarEntry("META-INF/maven/"+groupId+"/"+artifactId+"/pom.xml");
      if (pomEntry == null) {
        throw new InstallationException("No pom.xml found");
      }
      Path pomFile = Files.createTempFile(artifactId, ".pom").toAbsolutePath();
      try (InputStream is = zip.getInputStream(pomEntry)) {
        Files.copy(is, pomFile, StandardCopyOption.REPLACE_EXISTING);
      }

      JarEntry jarEntry = zip.getJarEntry("WEB-INF/lib/"+artifactId+".jar");
      if (jarEntry == null) {
        throw new InstallationException("No jar found");
      }
      Path jarFile = Files.createTempFile(artifactId, ".jar").toAbsolutePath();
      try (InputStream is = zip.getInputStream(jarEntry)) {
        Files.copy(is, jarFile, StandardCopyOption.REPLACE_EXISTING);
      }

      Artifact jarArtifact =
        new DefaultArtifact(groupId, artifactId, "", "jar", version)
          .setFile(jarFile.toFile());
      Artifact pomArtifact = new SubArtifact(jarArtifact, "", "pom")
        .setFile(pomFile.toFile());
      Artifact hpiArtifact = new SubArtifact(jarArtifact, "", "hpi")
        .setFile(pluginFile);

      InstallRequest installRequest = new InstallRequest();
      installRequest.addArtifact(jarArtifact).addArtifact(pomArtifact).addArtifact(hpiArtifact);
      repositorySystem.install(session, installRequest);

      Files.delete(pomFile);
      Files.delete(jarFile);
    }
  }
}
