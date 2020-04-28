package com.gt.jacoco.utils;

import com.gt.jacoco.config.Config;
import com.gt.jacoco.entity.RegisterInfo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GitUtils {

    private static final String REF_PREFIX = "refs/heads/";

    @Autowired
    private Config config;

    /**
     * 获取指定git工程目录下，指定分支的，指定文件内容
     *
     * @param absoluteProjectPath
     * @param filePath
     * @param branch
     * @return
     */
    public String getFile(String absoluteProjectPath, String filePath, String branch) {
        try (Git git = Git.open(new File(absoluteProjectPath))) {
            Repository repository = git.getRepository();
            Ref head = repository.exactRef(REF_PREFIX + branch);
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(head.getObjectId());
                try (TreeWalk treeWalk = TreeWalk.forPath(git.getRepository(), filePath, commit.getTree())) {
                    ObjectId blobId = treeWalk.getObjectId(0);
                    try (ObjectReader objectReader = repository.newObjectReader()) {
                        ObjectLoader objectLoader = objectReader.open(blobId);
                        byte[] bytes = objectLoader.getBytes();
                        return new String(bytes, StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public void cloneRepository(RegisterInfo registerInfo) throws GitAPIException {
        Git.cloneRepository()
                .setURI(registerInfo.getGitRepositoryUrl())
                .setDirectory(new File(registerInfo.getGitDir()))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(config.getGitAccount(), config.getGitPassword()))
                .call();
    }

    public void checkoutBranch(RegisterInfo registerInfo, String branch) throws GitAPIException {
        try (Git git = Git.open(new File(registerInfo.getGitDir()))) {
            List<Ref> branches = git.branchList().call();
            boolean exist = false;
            for (Ref ref : branches) {
                if (ref.getName().equals(REF_PREFIX + branch)) {
                    exist = true;
                }
            }
            if (exist) {
                git.checkout().setName(branch).call();
            } else {
                git.checkout().setCreateBranch(true).setName(branch).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM).setStartPoint("origin/" + branch).call();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void pull(RegisterInfo registerInfo, String branch) throws GitAPIException {
        try (Git git = Git.open(new File(registerInfo.getGitDir()))) {
            git.checkout().setName(branch).call();
            git.pull().setCredentialsProvider(new UsernamePasswordCredentialsProvider(config.getGitAccount(), config.getGitPassword())).call();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * 获取指定git工程目录下两条分支的差异文件
     *
     * @param absoluteProjectPath
     * @param newBranch
     * @param oldBranch
     * @return
     */
    public List<DiffEntry> findDifferenceFiles(String absoluteProjectPath, String oldBranch, String newBranch) {
        try (Git git = Git.open(new File(absoluteProjectPath))) {
            Repository repository = git.getRepository();
            AbstractTreeIterator oldTreeIterator = prepareTreeParser(repository, REF_PREFIX + oldBranch);
            AbstractTreeIterator newTreeIterator = prepareTreeParser(repository, REF_PREFIX + newBranch);
            return git.diff().setShowNameAndStatusOnly(true).setOldTree(oldTreeIterator).setNewTree(newTreeIterator).call()
                    .stream()
                    .filter(item -> item.getNewPath().endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, String ref) throws IOException {
        Ref head = repository.exactRef(ref);
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(head.getObjectId());
            RevTree tree = walk.parseTree(commit.getTree().getId());
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }
            walk.dispose();
            return treeParser;
        }
    }

}
