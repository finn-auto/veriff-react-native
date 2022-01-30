const {
  withPlugins,
  createRunOncePlugin,
  withProjectBuildGradle,
} = require('@expo/config-plugins');

const pkg = require('@veriff/react-native-sdk/package.json');

function getUpdatedProjectGradle(buildGradle) {
  const insertIndex = buildGradle.indexOf('mavenLocal()');
  const updatedBuildGradle = [
    buildGradle.slice(0, insertIndex),
    'maven { url "https://cdn.veriff.me/android/" } //veriff\n        ',
    buildGradle.slice(insertIndex),
  ].join('');

  return updatedBuildGradle;
}

function withVeriffProjectGradle(expoConfig) {
  return withProjectBuildGradle(expoConfig, config => {
    config.modResults.contents = getUpdatedProjectGradle(
      config.modResults.contents,
    );
    return config;
  });
}

function withVeriff(expoConfig) {
  return withPlugins(expoConfig, [withVeriffProjectGradle]);
}

exports.default = createRunOncePlugin(withVeriff, pkg.name, pkg.version);
