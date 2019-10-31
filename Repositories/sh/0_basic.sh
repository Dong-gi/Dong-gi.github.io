#! /bin/bash

echo 'Enter text : '
read text
echo 'Echo : '$text
echo ''


for small in 10 20 30
do
	let big=20
	if [ $small -gt $big ]
	then
		echo $small '>' $big
	elif [ $small -eq $big ]
	then
		echo $small '==' $big
	else
		echo $small '<' $big
	fi

	case $small in
	10 | 20 ) echo $small' is 10 or 20';;
	*) echo $small' is not 10 nor 20';;
	esac
done
echo ''


for entry in $(ls)
do
	echo $entry
done
echo ''


for num in {1..10..3}
do
	echo 'num in {1..10..3} > '$num
done
echo ''


while [ $num -gt 8 ]
do
	echo 'while num > 8 > '$num
	let num-=1
done
echo ''


until [ $num -gt 10 ]
do
	echo 'until num > 10 >'$num
	let num+=1
done
