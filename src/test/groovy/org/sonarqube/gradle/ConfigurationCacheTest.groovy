/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarqube.gradle

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ConfigurationCacheTest extends Specification {
    String gradleVersion = "8.13"

    @TempDir
    Path projectDir
    Path settingsFile
    Path buildFile
    Path outFile
    Path gradleProperties

    def setup() {
        settingsFile = projectDir.resolve('settings.gradle')
        buildFile = projectDir.resolve('build.gradle')
        outFile = projectDir.resolve('out.properties')
        gradleProperties = projectDir.resolve('gradle.properties')

        settingsFile << "rootProject.name = 'config-cache-test'"
        buildFile << """
            plugins {
                id 'java'
                id 'org.sonarqube'
            }

            sonar {
                properties {
                    property 'sonar.projectKey', 'org.sonarqube:config-cache-test'
                }
            }
        """
        gradleProperties << """
            org.gradle.configuration-cache=true
        """
    }

    def "test configuration cache"() {
        when:
        def firstRun = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('sonar', '--configuration-cache', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then:
        firstRun.task(":sonar").outcome == SUCCESS
        firstRun.output.contains("Configuration cache entry stored")
        !firstRun.output.contains("Configuration cache problems found")

        when:
        def secondRun = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('sonar', '--configuration-cache', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then:
        secondRun.task(":sonar").outcome == SUCCESS
        secondRun.output.contains("Configuration cache entry reused")
        !secondRun.output.contains("Configuration cache problems found")

        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.projectKey" == "org.sonarqube:config-cache-test"
    }
}