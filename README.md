# mergetool

通过jgit工具，git仓库回滚到firstcommit提交并创建工作分支，拷贝svn代码后merge回master分支来实现跨代码仓库的分支合并。

支持crc32校验，解决冲突后的文件，在未更改情况下解决重复merge问题。
