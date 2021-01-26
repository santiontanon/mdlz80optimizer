# vim integration

In order to use mldz80 in vim you have to do the following:

## Create a compiler file for mdlz80

In your vim configuration folder (usually `~/.vim`) create a `compiler/mdlz80optimizer.vim` with the content:
```
if exists("b:current_compiler") | finish | endif
let b:current_compiler = "mdlz80optimizer"

if exists(":CompilerSet") != 2		" older Vim always used :setlocal
  command -nargs=* CompilerSet setlocal <args>
endif

let s:cpo_save = &cpo
set cpo&vim

CompilerSet makeprg=java\ -jar\ ~/bin/mdl.jar\ $*\ -dialect\ asmsx\ -po
CompilerSet errorformat=INFO:\ Pattern-based\ optimization\ in\ %f#%l:\ %m

let &cpo = s:cpo_save
unlet s:cpo_save
```
In the line makeprg set the command you'll use. In this case case:
`CompilerSet makeprg=java\ -jar\ ~/bin/mdl.jar\ $*\ -dialect\ asmsx\ -po`
Which means, use the mdl.jar found in `/home/user/bin/`, pass the files and use dialect `asmsx` (change this to match your assembler) and option `-po`.

Then when editing you need to set MDL as the compiler with `:compiler mdlz80optimizer.vim` and then you'll be able to analyze the file with `:make file` and check the optimizations with `:copen`.

## Making it a bit more automatic

We can bind a key to process the file with mdlz80. In this example we're going to use `F6`.

In your vim config (`.vimrc`):
```
:nnoremap <F6> :compiler mdlz80optimizer \| :make % \| :cwindow <CR>
```
With this you'll be able to pass to mdlz80 the file, wait for it to end and then open the `cwindow` quickfix window to check the optimizations.

or if you want it to do the stuff in background with [vim-dispatch](https://github.com/tpope/vim-dispatch):
```
:nnoremap <F6> :compiler mdlz80optimizer \| :Make %<CR>
```
This way requires a plugin but it's a bit more elegant in the UI sense.
