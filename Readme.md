# Page Reload for Scala

## Use g8 template

```shell
sbt new mobilemindtech/pagereload4s.g8 
```

## Options

### Tasks

* `pagereloadStart` Start the reload plugin
* `pagereloadStop` Stop the reload plugin
* `pagereloadServe` Start http server
* `pagereloadWatch` Start file watcher

### Settings

* `pathWatchToReload: File | Null = null` Path to watch to reload on change
* `watchDefaultTargetToCopy: Boolean = true` Watch target folder to copy artifacts on changes
* `copyJsToPath: File | Null = null` Destination path to copy artifacts
* `copyJsToFile: File | Null = null` Copy js to a specific file, example /path/js/main.js
* `publicFolder: File | Null = null` Public folder to serve
* `debug: Boolean = false` Debug mode
* `notificationDelay: Long = 1000` Delay to send reload notification to websocket client
* `serverPort: Int = 10101` Server port
* `extensions: Seq[String] = Seq("js", "map", "css", "jpg", "jpeg", "png", "ico", "html")` File extensions to watch
* `reloadURL: String | Null = null` URL to reload on change, default is http://localhost:10101
                            


#### Use the plugin to copy files to an external location.

This plugin can be used to copy the generated JS to an external project. 
For example, to go lang or node application. 
In this case we need to specify the location where the js will be copied, 
the folder we want to watch to reload on change and the URL to reload. 

Config example:

Given the project structure:
```shell
my-go-app/
  public/
    js/
my-scalajs-app/
  src/
```

Configure the plugin in the build.sbt of the scalajs project:
```sbt
  .enablePlugins(ScalaJSPlugin, PageReload4sPlugin)
  .settings(
    pagereload := ReloadConfig(
       pathWatchToReload = baseDirectory.value / ".." / "my-go-app" / "public" / "js",
       copyJsToPath = baseDirectory.value / ".." / "my-go-app" / "public" / "js",
       reloadURL = "http://localhost:9000/myapp/mypage"
    )
  )
```

Add the script in your HTML page:

```html
    <script type="text/javascript" src="http://localhost:10101/js/livereload.js"></script>`
```

## Example

### plugins.sbt

```sbt
addSbtPlugin("br.com.mobilemind" % "pagereload4s" % "0.4.0")
```

### Public folder

```shell
example/
    src/
    public/
      index.html
      assets/
        js/
```

### build.sbt
```sbt

ThisBuild / name := "example"
ThisBuild / scalaVersion := "3.8.4"

lazy val app = (project in file("."))
  .enablePlugins(ScalaJSPlugin, PageReload4sPlugin)
  .settings(
    name := "example",
    scalaJSUseMainModuleInitializer := true,
    pagereload := ReloadConfig(
      pathWatchToReload = baseDirectory.value / "public",
      publicFolder = baseDirectory.value / "public",
      copyJsToPath = baseDirectory.value / "public" / "assets" / "js",
      debug = true
    ),
    libraryDependencies ++= Seq(
      "org.scala-js" %% "scalajs-dom" % "2.8.1"
    )
  )
	
```

## Usage

- Add on HTML page
```html

  <script type="text/javascript" src="http://localhost:10101/js/livereload.js"></script>
```

- In the sbt console execute

```
sbt:appjs> livereloadStart
```

- Run ~fastLinkJS to compile scalajs files.

```
sbt:appjs> ~fastLinkJS
```

When you change a file, the browser will be reloaded.

### Test project

- In the example project, run sbt:

```
sbt:appjs> pagereloadStart
```

- Open the test page on http://localhost:10101/sample/index.html.

```
sbt:appjs> ~fastLinkJS
```

- Change `Main.scala` and save to HTML reload.


## Publish

local

```
sbt:appjs> sbt publishLocal
```

maven central

```
sbt sonatypeBundleRelease
```