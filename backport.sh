git reset HEAD~1
rm ./backport.sh
git cherry-pick e9d13a38c1c1ee087c68e258e4e5c1a927a8e065
echo 'Resolve conflicts and force push this branch'
