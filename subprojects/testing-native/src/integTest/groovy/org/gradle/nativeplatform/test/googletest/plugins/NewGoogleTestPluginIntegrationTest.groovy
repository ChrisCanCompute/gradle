/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativeplatform.test.googletest.plugins

import org.eclipse.jgit.api.Git
import org.gradle.language.cpp.AbstractCppInstalledToolChainIntegrationTest
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.test.googletest.GoogleTestTestResults

class NewGoogleTestPluginIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest {
    def app = new CppHelloWorldApp()

    def "can run tests"() {
        def googleTestRepo = Git.cloneRepository().
            setDirectory(file("googletest")).
            setURI("https://github.com/gradle/googletest").
            call()
        googleTestRepo.close()
        file("googletest/settings.gradle").assertIsFile()

        settingsFile << """
            includeBuild "googletest"
        """
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: ${NewGoogleTestConventionPlugin.canonicalName}

            dependencies {
                testImplementation 'com.google:googletest:1.9.0'
            }

            tasks.withType(RunTestExecutable) {
                args "--gtest_output=xml:test_detail.xml"
            }
        """

        app.library.writeSources(file("src/main"))
        app.googleTestTests.writeSources(file("src/test"))

        expect:
        succeeds("googleTest")

        def testResults = new GoogleTestTestResults(file("build/test-results/test/test_detail.xml"))
        testResults.suiteNames == ['HelloTest']
        testResults.suites['HelloTest'].passingTests == ['test_sum']
        testResults.suites['HelloTest'].failingTests == []
        testResults.checkTestCases(1, 1, 0)
    }
}