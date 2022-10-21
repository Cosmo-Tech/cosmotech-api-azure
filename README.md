# cosmotech-api-azure
#### GitHub Packages

This project requires some public dependencies that are stored in GitHub Packages,
which requires users to be authenticated ([even for public repositories](https://github.community/t/download-from-github-package-registry-without-authentication/14407/131)).

You must therefore create a GitHub Personal Access Token (PAT) with the permissions below in order to [work with Maven repositories](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry):
- [read:packages](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages#about-scopes-and-permissions-for-package-registries)

Then add the following lines to your `~/.gradle/gradle.properties` file. Create the file if it does not exist.

```properties
gpr.user=[GITHUB_USERNAME]
gpr.key=[GITHUB_PAT]
```

