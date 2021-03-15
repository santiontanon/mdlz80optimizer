# Pattern Definition Language

This file provides some basic documentation on the definition of optimization patterns. See this file for examples: https://github.com/santiontanon/mdlz80optimizer/blob/master/src/main/resources/data/pbo-patterns.txt

An optimization pattern has the following form:

```
pattern: <text description>
<n1>: <instruction1>
...
<nn>: <instructionn>
replacement:
<nj>: <instructionj>
...
<nm>: <instructionm>
consraints:
<constraint1>
...
<constraintk>
```

## Text description:

This is just the message that will be shown to the user when the pattern is applied. It can contain variables (starting with ?) that will be replaced by their corresponding values.


## Pattern/Replacement:

Each line of the pattern section is of the form:

```
<number>: <instruction>
```

The number is just an index used to map instructions from the **pattern** to the **replacement**. If a number is present in pattern, and missing in the replacement, it means that the corresponding intruction will be removed. Conversely, if a number is present in the replacement but missing in the pattern, it means the corresponding instruction will be added. There is no special meaning to the numbers, and as long as they are unique, they can appear in any order. The only special number is ```0```, as it marks the source line in the code where the optimization message will appear.

Instructions can have three basic forms:

- A CPU instruction, like: ```ld a, 1```
- A ```*``` (a wildcard) that will match with 0 or more CPU instructions
- A repeated instruction, like: ```[n] ld a, 1```

A pattern must match with a contiguous set of instructions, and no labels are allowed for safety. Only the very first instruction with which a pattern matches can contain a label.

### Variables

Patterns can contain variables. There are 4 types of variables, identified by the name of the variables:
- ```?const```: for example ?const1, ?constant, etc. These match with constant expressions, like "(VAR+2)/$", or "1".
- ```?reg```: for example ?reg1, ?regpair, etc. These match with register names.
- ```?op```: for example ?op, ?op1, etc. These match with opcodes (e.g., ld, add, push, pop, etc.)
- ```?any```: for example ?any, ?any1. These match with anything.


### Constraints:

Any number of constraints can be specified, and all must be satisfied for a pattern to match. The following constraints are currently supported:

- ```regsNotUsedAfter(#, reg1, ..., regn)```: satisfied if the value of the specified registers is not used after the instructions in line # (where # is the number that precedes the insutructions in pattern/replacement). Notice that MDL will go forward in the program (in principle it could go through your whole assembler code) trying to see if the registers are ever used. If MDL encounters some instruction for which it cannot determine which is the next instruction (e.g. ```jp (hl)```) it will stop and the constraint fails for safety reasons.
- ```regsNotModified(#, reg1, ..., regn)```: if the registers are not modified in the instructions in line #
- ```regsNotUsed(#, reg1, ..., regn)```: if the registers are not used in the instructions in line #
- ```flagsNotUsedAfter(#, flag1, ..., flagn)```: same as regsNotUsedAfter but for flags
- ```flagsNotModified(#, flag1, ..., flagn)```: same as regsNotModified but for flags
- ```flagsNotUsed(#, flag1, ..., flagn)```: same as regsNotUsed but for flags
- ```equal(exp1, exp2)```: if the value of exp1 is the same as the value for exp2, where exp1/exp2 are expressions.
- ```notEqual(exp1, exp2)```: if the value of exp1 is not the same as the value for exp2, where exp1/exp2 are expressions.
- ```in(variable, value1, ..., valuen)```: if the value of variable ```variable``` is one of the specified ones.
- ```notIn(variable, value1, ..., valuen)```: if the value of variable ```variable``` is not one of the specified ones.
- ```regpair(regpair,regpair_high,regpair_low)```: checks that regpair is a register pair (BC, DE, HL, IC, IY) made up of the two subregisters regpair_high,regpair_low.
- ```reachableByJr(#, label)```: satisfied if ```label``` is reacheable via a short ```jr``` jump from instruction #
- ```evenPushPops(#)```: satisfied if there is the same number of push instructions than of pop instructions in the instruction block # (this usually denotes a wildcard). If at any point there are more pops than pushs, this condition will also fail.
- ```atLeastOneCPUOp(#)```: satisfied if there is at least one CPU instruction in the lines matched by # (usually a wildcard).


