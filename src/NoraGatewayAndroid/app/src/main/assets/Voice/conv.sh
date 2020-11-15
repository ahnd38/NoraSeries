#!/bin/sh

for file in `\find . -maxdepth 1 -type f`; do
    wav2ambe "./$file" "./$file.ambe"
done

