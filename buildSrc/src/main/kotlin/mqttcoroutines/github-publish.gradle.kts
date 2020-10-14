package mqttcoroutines

plugins{
    id("maven-publish")
}

/*
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            */
/** Configure path of your package repository on Github
             *  Replace GITHUB_USERID with your/organisation Github userID and REPOSITORY with the repository name on GitHub
             *//*

            url = uri("https://maven.pkg.github.com/sorinirimies/MqttCoroutines") // Github Package
            credentials {
                //Fetch these details from the properties file or from Environment variables
                username = githubProperties.get("gpr.usr") as String? ?: System.getenv("GPR_USER")
                password = githubProperties.get("gpr.key") as String? ?: System.getenv("GPR_API_KEY")
            }
        }
    }
}*/
