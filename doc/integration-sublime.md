## Sublime Text integration

The output of MDL pattern-based optimizer can be integrated into Sublime Text using a build system. Here is an exampe build (go to Build -> Build System -> New Build System... to enter this script):

```json
{
	"cmd": ["java", "-jar", "java/mdl.jar", "$file", "-po"],
	"working_dir": "$folder",
	"file_regex": "[INFO|WARNING]: .+ in (.+)#([0-9]+):() (.+)",
}
```

Of course, the location of mdl.jar in your system might vary. The script above assumes that there is a folder called "java" in your project folder with mdl.jar inside. Also, here we are only using the "-po" flag (to call the pattern-based optimizer with default settings). Add other flags (e.g., "-ro", or the dialect selection flags), to configure MDL to your own needs.
