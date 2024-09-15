#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.0.0")
@file:DependsOn("dependabot:fetch-metadata:v2.2.0")

import io.github.typesafegithub.workflows.actions.dependabot.FetchMetadata_Untyped
import io.github.typesafegithub.workflows.domain.Mode.Write
import io.github.typesafegithub.workflows.domain.Permission.Contents
import io.github.typesafegithub.workflows.domain.Permission.PullRequests
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.dsl.expressions.Contexts
import io.github.typesafegithub.workflows.dsl.expressions.Contexts.github
import io.github.typesafegithub.workflows.dsl.expressions.Contexts.secrets
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig.Disabled

fun orExpr(vararg expressions: String): String = expressions.joinToString(" || ") { "($it)" }
infix fun String.equalsExpr(other: String): String = "$this == $other"
fun stringExpr(value: String): String = "'$value'"

val on = listOf(
    PullRequest(),
)
val actorIsDependabot = expr {
    github.actor equalsExpr stringExpr("dependabot[bot]")
}
workflow(
    consistencyCheckJobConfig = Disabled,
    name = "Dependabot auto-merge",
    on = on,
    permissions = mapOf(
        PullRequests to Write,
        Contents to Write,
    ),
    sourceFile = __FILE__,
    targetFileName = "auto-merge.yml",
) {
    job(
        id = "dependabot",
        runsOn = UbuntuLatest,
        `if` = actorIsDependabot,
    ) {
        val metadata = uses(
            name = "Dependabot metadata",
            action = FetchMetadata_Untyped(),
            env = mapOf(
                "GITHUB_TOKEN" to expr(secrets.GITHUB_TOKEN),
            ),
        )

        val PR_URL by Contexts.env
        run(
            name = "Enable auto-merge for Dependabot PRs",
            `if` = expr {
                orExpr(
                    metadata.outputs["update-type"] equalsExpr stringExpr("version-update:semver-minor"),
                    metadata.outputs["update-type"] equalsExpr stringExpr("version-update:semver-patch"),
                )
            },
            command = "gh pr merge --auto --squash \"$PR_URL\"",
            env = mapOf(
                "PR_URL" to expr(github.eventPullRequest.pull_request.html_url),
                "GITHUB_TOKEN" to expr(secrets.GITHUB_TOKEN),
            ),
        )
    }
}


workflow(
    consistencyCheckJobConfig = Disabled,
    name = "Dependabot auto-approve",
    on = on,
    permissions = mapOf(
        PullRequests to Write,
    ),
    sourceFile = __FILE__,
    targetFileName = "auto-approve.yml",
) {
    job(
        id = "dependabot",
        runsOn = UbuntuLatest,
        `if` = actorIsDependabot,
    ) {
        uses(
            name = "Dependabot metadata",
            action = FetchMetadata_Untyped(),
            env = mapOf(
                "GITHUB_TOKEN" to expr(secrets.GITHUB_TOKEN),
            ),
        )
        val PR_URL by Contexts.env
        run(
            name = "Approve a PR",
            command = "gh pr review --approve \"$PR_URL\"",
            env = mapOf(
                "PR_URL" to expr(github.eventPullRequest.pull_request.html_url),
                "GITHUB_TOKEN" to expr(secrets.GITHUB_TOKEN),
            ),
        )
    }
}
