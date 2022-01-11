# Search-based Optimizer Specification

This file provides some basic documentation about how to use the search-based optimizer (SO) to generate code automatically. The SO can be used in two ways, if the input file is an assembler file, it will try to optimize it, but if the input file is a specification file (MDL detects this if the input file has ```.txt``` extension, or if you explicitly use the ```-so-gen``` flag), then it will try to generate optimal code given a specification. This later case is what is covered in this doc.

For example, if you create this specification (say, in ```test.txt```):

```
allowed_ops: 
    shift
    ld
allow_ram_use = false
allowed_registers: hl, a
initial_state:
    HL = ??val
goal_state:
    HL = ??val << 10
```

And then call MDL like this:

```java -jar mdl.jar test.txt -so```

The SO will generate the following code:

```
    ld h, l
    ld l, 0
    sla h
    sla h
```

The code generated acts internally as if you had loaded assembler from an input file, so, you can then use it in MDL to do whatever you want. For example, if you wanted to save it to disk, you could call MDL like this:

```java -jar mdl.jar test.txt -so -asm test.asm```

## The Specification File

The specification file has two mandatory fields (```initial_state:``` and ```goal_state:```), which define the program you want to generate. The rest of fields are optional and are used to control the way MDL will search for the optimal program to satisfy your specification. You can enter fields in any order you want. The fields currently supported are:

- ```allowed_ops:``` This defines the set of CPU instructions that MDL is allowed to use to generate your program. You can specify them one by one by name (e.g. ```ld, add, adc, xor```), or you can specify them by groups. Currently, the following groups are defined as shortcuts: ```logic``` (```and, or, xor```), ```increment``` (```inc, dec```), ```addition``` (```add, adc, sub, sbc```), ```rotation``` (```rlc, rl, rrc, rr, rlca, rla, rrca, rra```), ```shift``` (```sla, sra, srl, sli```), ```negation``` (```cpl, neg```), ```bits``` (```bit, set, res```), ```carry``` (```ccf, scf```) and ```jump``` (```jp, jr, djnz```). Not all instructions are supported yet. In addition to the instructions in the groups above, the only other instructions that are supported are ```ld```, ```cp``` and ```ex```. The smaller the instruction set, the smaller the search space, and the faster the search. So, try to reduce the instruction set as much as possible. If no ```allowed_ops``` is specified, MDL will try to use all the instructions it knows of.

- ```allowed_registers:``` This specifies the set of registers MDL can use when generating your program. Again, try to reduce as much as possible. By default, all registers are used.

- ```initial_state:``` This is a list of CPU registers and memory addresses with the initial values they should take at the beginning of the program. You can use constants, e.g. ```a = #0f```, define parameters, like ```a = ?val```, or combine them into any expressions you want, like ```a = (val * 2) << 3```. Parameters can be 8bit or 16bit, MDL will try to autodetect this (8bit if used to initialize an 8 bit register, and 16bit otherwise). But you can overwrite this by defining variables preceded by a question mark (```?val```) to indicate 8bit, or two question marks (```??val```) to indicate 16bit. To specify memory content, you can specify it use expressions like ```(#c000) = 5```.

- ```goal_state:``` This is a list of CPU registers, flags and memory addresses with the expected values they should take at the end of the program. You can use constants, parameters or expressions. All the parameters used must have been defined in the ```initial_state```. When specifying output flags, use the following names: ```c_flag```, ```n_flag```, ```pv_flag```, ```h_flag```, ```z_flag```, and ```s_flag```. You can specify a flag condition, for example, like this ```z_flag = (?val1 == ?val2)``` (parentheses are not needed, but added for clarity).

- ```8bit_constants:``` This defines the set of constants that MDL can use when constructing instructions with 8bit values, e.g. ```ld a, 1```. By default, only the constant ```0``` is used to accelerate search. But you can tell MDL to use other constants. You can specify individual values or ranges. For example, like this (which allows MDL to use the constants 1, 2, 8, 9, 10, 11, 12, 13, 14, 15 and 16):

```
8bit_constants:
  1, 2
  8 - 16
```

- ```16bit_constants:``` the same as above, but for 16bit constants.

- ```offset_constants:``` the same as above, but for offsets used in instructions of the style ```ld a, (ix + 1)```.

- ```max_ops = [value]``` defines the maximum number of CPU instructions the program can have (4 by default). Unless you constraint the search space a lot, do not expect to be able to generate programs with more than 4, 5 or maybe 6 instructions in a reasonable amount of time. Remember the space of possible assembler programs is exponential on the length of the program. This can be overriden via flats to MDL.

- ```max_size = [size in bytes]``` defines the maximum size in bytes of the program to search (256 by default). This can be overriden via flats to MDL.

- ```max_time = [time in t-states/nops/or whatever unit the CPU you selected uses]``` defines the maximum execution time of the program to search for (256 by default). This can be overriden via flats to MDL.

- ```allow_ram_use = [true/false]``` whether MDL can use RAM or not (```false``` by default). If this is ```false```, instructions like ```ld a,(hl)``` will not be used.

- ```allow_loops = [true/false]```  (```false``` by default) if this is set to ```true``` and jump instructions (like ```jp```) are allowed during search, jumps backwards (that might create loops) are allowed, otherwise, they are not considered. If you allow loops, recall that if your program takes more than ```max_time``` time units to execute, it will be considered to fail.

- ```goal = [ops/size/time/opssafe]``` defines the goal to optimize for. By default it's ```opssafe``` (number of instructions but ensuring at least improving either time or size, which is the easiest optimization goal). This can be overriden via flats to MDL.

- ```n_solution_checks = [integer]``` defines the number of test cases used to determine if a solution is correct or not. The default is 1000. Feel free to increment this to 2000 or even larger, as it shuold not affect execution speed much (most cases fail early anyway).

A complete example using all the fields above could be:

```
allowed_ops: 
    logic
    increment
    ld
    addition
allowed_registers:
    hl, bc, a
8bit_constants:
    0
16bit_constants:
    0 - 2
    0x04
max_ops = 4
max_size = 12
max_time = 80
allow_ram_use = false
goal = ops
initial_state:
    HL = val1
    A = val2
goal_state:
    HL = val1 + val2
```

If you then run: ```java -jar mdl.jar test.txt -so```

You should see the following output:
```
INFO: SearchBasedOptimizer: depth 0 complete (1 solutions tested)
INFO: SearchBasedOptimizer: depth 1 complete (18 solutions tested)
INFO: SearchBasedOptimizer: depth 2 complete (910 solutions tested)
INFO: New solution found after 36319 solutions tested (size: 4, time: 25):
INFO:     ld b, 0
INFO:     ld c, a
INFO:     add hl, bc
INFO: SearchBasedOptimizer: search ended (42187 solutions tested)
```

This means that MDL had to test 42187 programs until it found one that satisfied the specifications. If you want to optimize by execution time, you could call MDL like this:

```java -jar mdl.jar test.txt -so-time```

In this case, the optimal program happens to be the same, but this does not have to be the case. You will also see that optimizing for execution time, MDL will end up exploring 4397923 programs (since optimizing for execution time is harder).

If you want to safe the resulting program to disc, you can call MDL like:

```java -jar mdl.jar test.txt -so -asm test.asm```

And of course, if you just want to generate the assembler program, but with no messages printed to the console, you can use the ```-quiet``` flag, as with any other MDL worker:

```java -jar mdl.jar test.txt -so -asm test.asm -quiet```

