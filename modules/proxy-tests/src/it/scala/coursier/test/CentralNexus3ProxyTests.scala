package coursier.test

import coursier.maven.MavenRepository

object CentralNexus3ProxyTests extends CentralTests {

  val repo = NexusDocker(
    "sonatype/nexus3:3.3.1",
    "repository/maven-central/", // 400 error without the trailing '/'
    9082
  )

  override def utestAfterAll(): Unit =
    repo.shutdown()

  override def central =
    MavenRepository(repo.base.stripSuffix("/"))
      .withVersionsCheckHasModule(false)
}
