# MDL (a Z80 assembler optimizer)
Santiago Ontañón (Brain Games)

I spend an enourmous amount of time optimizing the Z80 assembler code of my games to make them fit within small 32KB or 48KB cartridges, and to make them run fast enough. So, I thought I'd try to write a tool that automatically does some of the optimizations that I do manually. I named it MDL after the "minimum description length" principle since, in a way, the goal of MDL is to reach the minimum description length representation of a program (although it currently only does simple optimizations).

MDL (Minimum Description Length), is a command line tool to optimize Z80 assembler code. It is distributed as a Java JAR file, and from the command line, you can launch it like this:

```
java -jar mdl.jar
```

Moreover, mdl accepts a number of command line arguments in order to make it do what you want (see below).

The latest version can always be downloaded from the "releases" section: https://github.com/santiontanon/mdlz80optimizer/releases

I also recorded a series of videos explaining how does MDL work:
- Version 1.4: https://www.youtube.com/watch?v=yVniGPu-znc (in Spanish), https://www.youtube.com/watch?v=2M8la7TuCzw&t=1s (in English)
- Introduction to the main idea (earlier versions): https://www.youtube.com/watch?v=g5aoF4-r4v4 , https://www.youtube.com/watch?v=TCtm3FRz45c , and https://www.youtube.com/watch?v=30SEguEDWp0 (in English)

## Command Line Arguments

```java -jar mdl.jar <input assembler file> [options/tasks]```

Note: notice that all the tasks concerning generating outputs (assembler, binaries, etc.) will be executed after the optimizers are run.

- ```-help```: to show this information (this is the only flag that can be used without specifying an input file).
- ```-cpu <type>```: to select a different CPU (z80/z80msx/z80cpc) (default: z80msx).
- ```-dialect <dialect>```: to allow parsing different assembler dialects (mdl/asmsx/asmsx-zilog/glass/sjasm/sjasmplus/tniasm/winape/pasmo/sdcc/sdasz80/macro80) (default: mdl, which supports some basic code idioms common to various assemblers).
                   Note that even when selecting a dialect, not all syntax of a given assembler might be supported.
- ```-I <folder>```: adds a folder to the include search path.
- ```-equ <symbol>=<value>```: defines a symbol that will exist while parsing the assembler code.
- ```-ansion```: turns on color message output usin ANSI codes (default: on in Unix, off in Windows).
- ```-ansioff```: turns off color message output usin ANSI codes.
- ```-quiet```: turns off info messages; only outputs warnings and errors.
- ```-debug```: turns on debug messages.
- ```-trace```: turns on trace messages.
- ```-warn```: turns on all warnings.
- ```-warn-labelnocolon```: turns on warnings for not placing colons after labels.
- ```-warn-jp(rr)```: turns on warnings for using confusing 'jp (hl)' instead of 'jp hl' (this is turned off by default in dialects that do not support this).
- ```-warn-unofficial```: turns on warnings for using unofficial op syntax (e.g., 'add 1' instead of 'add a,1'.
- ```-warn-ambiguous```: turns on warnings for using ambiguous or error-inducing syntax in some dialects.
- ```-do-not-convert-to-official```: turns off automatic conversion of unofficial op syntax to official ones in assembler output.
- ```-hex#```: hex numbers render like #ffff (default). These flags also have analogous effect on binary and octal constant rendering.
- ```-HEX#```: hex numbers render like #FFFF.
- ```-hexh```: hex numbers render like 0ffffh.
- ```-HEXH```: hex numbers render like 0FFFFh.
- ```-hex0x```: hex numbers render like 0xffff.
- ```-HEX0X```: hex numbers render like 0XFFFF.
- ```-+bin```: includes binary files (incbin) in the output analyses.
- ```-no-opt-pragma <value>```: changes the pragma to be inserted in a comment on a line to prevent optimizing it (default: mdl:no-opt)
- ```-no-opt-start-pragma <value>```: changes the pragma to be inserted in a comment on a line to mark it as the start of a block of lines to be protected from optimization (default: mdl:no-opt-start)
- ```-no-opt-end-pragma <value>```: changes the pragma to be inserted in a comment on a line to mark it as the end of a block of lines to be protected from optimization (default: mdl:no-opt-end)
- ```-out-opcase <case>```: whether to convert the assembler operators to upper or lower case. Possible values are: none/lower/upper (none does no conversion). Default is 'lower'.
- ```-out-allow-ds-virtual```: allows 'ds virtual' in the generated assembler (not all assemblers support this, but simplifies output)
- ```-out-colonless-equs```: equs will look like 'label equ value' instead of 'label: equ value'
- ```-out-remove-safety-equdollar```: labels preceding an equ statement are rendered as 'label: equ $' by default for safety (some assemblers interpret them differently otherwise). Use this flag to deactivate this behavior.
- ```-out-labels-no-dots```: local labels get resolved to `context.label', some assemblers do not like '.' in labels however. This flag replaces them by underscores.
- ```-out-squarebracket-ind```: use [] for indirections in the output, rather than ().
- ```-out-data-instead-of-ds```: will replace statements like ```ds 4, 0``` by ```db 0, 0, 0, 0```.
- ```-out-do-not-evaluate-dialect-functions```: some assembler dialects define functions like random/sin/cos that can be used to form expressions. By default, MDL replaces them by the result of their execution before generating assembler output (as those might not be defined in other assemblers, and thus this keeps the assembler output as compatible as possible). Use this flag if you don't want this to happen.
- ```-out-evaluate-all-expressions```: this flag makes MDL resolve all expressions down to their ultimate numeric or string value when generating assembler code.
- ```-safety-labels-for-jumps-to-constants```: makes MDL replace the destination of a jump/call to a constant (e.g. ```jp #3c4a```) by a label. MDL does not do this by default since calls to constants are often used for BIOS calls (although replacing those constants by labels is recommended). Jumpts to constants are unsafe for optimization as the code at the target address (```#3c4a``` in the example) might move as a result of optimization. Hence, it's safer to add a safety label at the target address and use it for the jump.
- ```-so```: (new in version 2.0) Runs the search-based-based optimizer (input file is a function specification instead of an assembler file).
- ```-so-ops```/```-so-size```/```-so-time```: Runs the optimizer with a specific optimization goal (minimize the number of CPU ops, number of bytes, or execution time). This will overwrite whatever is specified in the specificaiton file (default is to optimize by number of ops).
- ```-so-maxops <n>```: Sets the upper limit of how many CPU ops the resulting program can have.
- ```-so-maxsize <n>```: Sets the maximum number of bytes the resulting program can occupy.
- ```-so-maxtime <n>```: Sets the maximum time (in whichever units the target CPU uses) that the resulting program can take to execute.
- ```-ro```: runs the code reoganizer optimizer.
- ```-ro-no-inliner```: deactivates the function inliner.
- ```-ro-no-merger```: deactivates the block merger.
- ```-rohtml <file>```: generates a visualization of the division of the code before code reoganizer optimization as an html file.
- ```-po```: Runs the pattern-based optimizer using the latest settings. You can pass an optional parameter, like ````-po size``` or ```-po speed```, which are shortcuts for '-po -popatterns data/pbo-patterns-size.txt' and '-po -popatterns data/pbo-patterns-speed.txt' (some dialects might change the defaults of these two)
- ```-po1```/```-po2```/```-po3```: The same as ```-po```, but specify whether to do 1, 2 or 3 passes of optimization (```-po``` is equivalent to ```-po2```). The more passes, the slower the optimization. Usually 1 pass is enough, but often 2 passes finds a few additional optimizations. 3 passes rarely finds any additional optimization.
- ```-posilent```: Supresses the pattern-based-optimizer output
- ```-popotential```: Reports lines where a potential optimization was not applied for safety, but could maybe be done manually (at most one potential optimization per line is shown).
- ```-popotential-all```: Same as above, but without the one-per-line constraint.
- ```-popatterns <file>```: specifies the file to load optimization patterns from (default 'data/pbo-patterns.txt', which contains patterns that optimize both size and speed). For targetting size optimizations, use 'data/pbo-patterns-size.txt'. Notice that some dialects might change the default, for example, the sdcc dialect sets the default to 'data/pbo-patterns-sdcc-speed.txt'
- ```-po-ldo```: some pattern-based optimizations depend on the specific value that some labels take ('label-dependent optimizations', ldo). These might be dangerous for code that is still in development.
- ```-dot <output file>```: generates a dot file with a graph representing the whole source code. Convert it to a png using 'dot' like this: ```dot -Tpng <output file>.dot -o <output file>.png```
- ```-st <output file>```: to output the symbol table.
- ```-st-constants```: includes constants, in addition to labels, in the output symbol table.
- ```-sft <output file>```: generates a tsv file with some statistics about the source files.
- ```-asm <output file>```: saves the resulting assembler code in a single asm file (if no optimizations are performed, then this will just output the same code read as input (but with all macros and include statements expanded). Use ```auto``` as the output file name to respect the filenames specified in the sourcefiles of some dialects, or to auto generate an output name.
- ```-asm-dialect <output file>```: same as '-asm', but tries to mimic the syntax of the defined dialect in the output (experimental feature, not fully implemented!).  Use ```auto``` as the output file name to respect the filenames specified in the sourcefiles of some dialects, or to auto generate an output name.
- ```-asm-expand-incbin```: replaces all incbin commands with their actual data in the output assembler file, effectively, making the output assembler file self-contained.
- ```-asm+ <output file>```: generates a single text file containing the original assembler code (with macros expanded), that includes size and time annotations at the beginning of each file to help with manual optimizations beyond what MDL already provides.
- ```-asm+:html <output file>```: acts like ```-asm+```, except that the output is in html (rendered as a table), allowing it to have some extra information.
- ```-bin <output file>```: generates an assembled binary. Use ```auto``` as the output file name to respect the filenames specified in the sourcefiles of some dialects, or to autogenerate an output name.


## How to use MDL

### Integrating it into an IDE

The most commonly expected use of MDL is to integrate it into your text editor (vim, Subline Text, VSCode, etc.). We currently provide three examples of how to integrate it into vim, Sublime and VSCode, but if you want to integrate it into another editor/IDE and do not know how, please do let me know!:

- https://github.com/santiontanon/mdlz80optimizer/blob/master/doc/integration-vim.md  (thanks to Fubukimaru!)
- https://github.com/santiontanon/mdlz80optimizer/blob/master/doc/integration-sublime.md
- https://github.com/santiontanon/mdlz80optimizer/blob/master/doc/integration-vscode.md  (thanks to theNestruo!)

Here is an example of how does it look when integrated into Sublime Text:

<img src="https://github.com/santiontanon/mdlz80optimizer/blob/master/media/mdl-sublime.png?raw=true" alt="mdl in sublime" width="640"/>


### Optimizing Assembler

Alternatively, you can ask MDL to directly generate optimized code Imagine you have a Z80 assembler project (with main file ```main.asm```), you can just do this:

```
java -jar mdl.jar main.asm -po -asm main-optimized.asm
```

The first parameter specifies the input assembler file. ```-po``` tells MDL to run the "pattern-based optimizer", and ```-asm``` specifies the output file to where you want to save the optimized assembler code.

MDL's optimizer doesn't know anything about macros, include statements, and other fancy syntax that is usually included in assembler files. So, MDL's pre-processor loads the assembler files, and runs a pre-processor to resolve all macros before running the optimizer. Thus, the generated code will not contain any macros, as those will all be expanded. Therefore, if you call MDL without ```-po``` it will not run the optimizer, it will just spit out the same assembler code it loaded as input (but with all the macros expanded).

If you just want to run the optimizer, tell you the potential optimizations but not save any optimized code, you can just call MDL like this:

```
java -jar mdl.jar main.asm -po
```

This will just output to the terminal all the suggested optimizations, and you can choose which ones to do manually.

For documentation on how to define your own optimization patterns, see: https://github.com/santiontanon/mdlz80optimizer/blob/master/doc/pattern-definition.md

### Other MDL Functionalities

MDL includes several other functionalities, aimed at helping optimizing Z80 assembler code. For example, it can generate "annotated assembler" to help you see how much space each assembler statement uses and make decisions about how to optimize. You can generate this annotated assembler output by calling MDL like this:

```
java -jar mdl.jar main.asm -asm+ main-annotated.txt
```

or

```
java -jar mdl.jar main.asm -asm+:html main-annotated.html
```


Of course you could also add a ```-po``` or ```-ro``` there, if you want to optimize the code before annotating it.

MDL can also generate tables with how much space each of your assembler files uses (if you include many files from a main assembler file, MDL will analyze all of them), and can even generate a little visual reprsentation of your code (saved as a standard .dot file that can then be turned into a pdf or png image to view it using the [dot](https://graphviz.org) tool).


## Requirements

- MDL is a command line tool, so you need access to a terminal

- Java version 8 or higher installed in your computer
