plugins {
    id("geyser.shadow-conventions")
    id("net.kyori.indra.publishing")
}

indra {
    configurePublications {
        if (false && shouldAddBranchName()) {
            version = versionWithBranchName()
        }
    }

//    publishSnapshotsTo("geysermc", "https://repo.opencollab.dev/maven-snapshots")
//    publishReleasesTo("geysermc", "https://repo.opencollab.dev/maven-releases")
    publishSnapshotsTo("SunnyInfra", "https://repo.sunnyinfra.cloud/development")

}
