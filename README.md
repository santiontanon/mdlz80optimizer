# MDL (a Z80 assembler optimizer)
Santiago Ontañón (Brain Games)

I spend an enourmous amount of time optimizing the Z80 assembler code of my games to make them fit within small 32KB or 48KB cartridges, and to make them run fast enough. So, I thought I'd try to write a tool that automatically does some of the optimizations that I do manually. I named it MDL after the "minimum description length" principle since, in a way, the goal of MDL is to reach the minimum description length representation of a program (although it currently only does **very** simple optimizations).

MDL (Minimum Description Length), is a command line tool to optimize Z80 assembler code. It is distributed as a Java JAR file, and from the command line, you can launch it like this:

```
java -jar mdl.jar
```

Moreover, mdl accepts a number of command line arguments in order to make it do what you want (see below).

The latest version can always be downloaded from the "releases" section: https://github.com/santiontanon/mdlz80optimizer/releases

## Command Line Arguments

```java -jar mdl.jar <input assembler file> [options]```

  ```-cpu <type>```: to select a different CPU (z80/z80msx/z80cpc) (default: z80msx).

  ```-dialect <type>```: to allow parsing different assembler dialects (mdl/asmsx/glass/sjasm/tniasm/winape/pasmo/sdcc) (default: mdl, which supports some basic code idioms common to various assemblers).
                   Note that even when selecting a dialect, not all syntax of a given assembler might be supported.

  ```-I <folder>```: adds a folder to the include search path.

  ```-quiet```: turns off info messages; only outputs warnings and errors.
  
  ```-debug```: turns on debug messages.
  
  ```-trace```: turns on trace messages.
  
  ```-warn```: turns on all warnings.
  
  ```-warn-labelnocolon```: turns on warnings for not placing colons after labels.
  
  ```-warn-jp(rr)```: turns on warnings for using confusing 'jp (hl)' instead of 'jp hl' (this is turned off by default in dialects that do not support this).
  
  ```-warn-unofficial```: turns on warnings for using unofficial op syntax (e.g., 'add 1' instead of 'add a,1'.
  
  ```-warn-ambiguous```: turns on warnings for using ambiguous or error-inducing syntax in some dialects.  
    
  ```-do-not-convert-to-official```: turns off automatic conversion of unofficial op syntax to official ones in assembler output.
  
  ```-hex#```: hex numbers render like #ffff (default).

  ```-HEX#```: hex numbers render like  #FFFF.

  ```-hexh```: hex numbers render like  0ffffh.

  ```-HEXH```: hex numbers render like  0FFFFh.

  ```-+bin```: includes binary files (incbin) in the output analyses.

  ```-no-opt-pragma <value>```: changes the pragma to be inserted in a comment on a line to prevent optimizing it (default: mdl:no-opt)

  ```-out-opcase <case>```: whether to convert the assembler operators to upper or lower case. Possible values are: none/lower/upper (none does no conversion). Default is 'lower'.
  
  ```-out-allow-ds-virtual```: allows 'ds virtual' in the generated assembler (not all assemblers support this, but simplifies output)
  
  ```-out-colonless-equs```: equs will look like 'label equ value' instead of 'label: equ value'
  
  ```-out-remove-safety-equdollar```: labels preceding an equ statement are rendered as 'label: equ $' by default for safety (some assemblers interpret them differently otherwise). USe this flag to deactivate this behavior.
  
  ```-out-labels-no-dots```: local labels get resolved to `context.label', some assemblers do not like '.' in labels however. This flag replaces them by underscores.
  
  ```-out-squarebracket-ind```: use [] for indirections in the output, rather than ().
  
  ```-out-data-instead-of-ds```: will replace statements like 'ds 4, 0' by 'db 0, 0, 0, 0.
  
  ```-do-not-evaluate-dialect-functions```: some assembler dialects define functions like random/sin/cos that can be used to form expressions. By default, MDL replaces them by the result of their execution before generating assembler output (as those might not be defined in other assemblers, and thus this keeps the assembler output as compatible as possible). Use this flag if you don't want this to happen.
  
  ```-evaluate-all-expressions```: this flag makes MDL resolve all expressions down to their ultimate numeric or string value when generating assembler code.
  
  ```-po```: Runs the pattern-based optimizer (notice that using any of the -po* flags also has the same effect of turning on the pattern-based optimized). You can pass an optimal parameter, like '-po size' or '-po speed', which are shortcuts for '-po -popatterns data/pbo-patterns-size.txt' and '-po -popatterns data/pbo-patterns-speed.txt'
  
  ```-posilent```: Supresses the pattern-based-optimizer output
  
  ```-poapply```: For each assembler <file> parsed by MDL, a corresponding <file>.mdl.asm is generated with the optimizations applied to it.
  
  ```-popotential```: Reports lines where a potential optimization was not applied for safety, but could maybe be done manually (at most one potential optimization per line is shown).
  
  ```-popotential-all```: Same as above, but without the one-per-line constraint.  

  ```-popatterns <file>```: specifies the file to load optimization patterns from (default 'data/pbo-patterns.txt')

  ```-dot <output file>```: generates a dot file with a graph representing the whole source code. Convert it to a png using 'dot' like this: dot -Tpng <output file>.dot -o <output file>.png

  ```-st <output file>```: to output the symbol table.

  ```-st-constants```: includes constants, in addition to labels, in the output symbol table.

  ```-sft <output file>```: generates a tsv file with some statistics about the source files.

  ```-asm <output file>```: saves the resulting assembler code in a single asm file (if no optimizations are performed, then this will just output the same code read as input (but with all macros and include statements expanded).

  ```-asm-expand-inbcin```: replaces all incbin commands with their actual data in the output assembler file, effectively, making the output assembler file self-contained.

  ```-asm+ <output file>```: generates a single text file containing the original assembler code (with macros expanded), that includes size and time annotations at the beginning of each file to help with manual optimizations beyond what MDL already provides.

## How to use MDL

### Integrating it into an IDE

The most commonly expected use of MDL is to integrate it into your text editor (Subline Text, VSCode, etc.). We currently provide two examples of how to integrate it into Sublime and VSCode, but if you want to integrate it into another editor/IDE and do not know how, please do let me know!:

- https://github.com/santiontanon/mdlz80optimizer/blob/master/integration-sublime.md
- https://github.com/santiontanon/mdlz80optimizer/blob/master/integration-vscode.md

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


### Other MDL Functionalities

MDL includes several other functionalities, aimed at helping optimizing Z80 assembler code. For example, it can generate "annotated assembler" to help you see how much space each assembler statement uses and make decisions about how to optimize. You can generate this annotated assembler output by calling MDL like this:

```
java -jar mdl.jar main.asm -asm+ main-annotated.txt
```

Of course you could also add a ```-po``` there, if you want to optimize the code before annotating it.

MDL can also generate tables with how much space each of your assembler files uses (if you include many files from a main assembler file, MDL will analyze all of them), and can even generate a little visual reprsentation of your code (saved as a standard .dot file that can then be turned into a pdf or png image to view it using the [dot](https://graphviz.org) tool).


### Optimizing Assembler

Integration with IDEs/text editors. Please check separate read me files for how to integrate mdl into VSCode and Sublime Text.


## Requirements

- MDL is a command line tool, so you need access to a terminal

- Java version 8 installed in your computer
