package org.vno.gateway.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vno.gateway.bridge.MongoBridge;
import org.vno.gateway.bridge.NeoBridge;
import org.vno.gateway.domain.Blob;
import org.vno.gateway.domain.Commit;

import java.util.ArrayList;
import java.util.List;

/**
 * @author kk
 */
@RequestMapping("/r")
@RestController
@PreAuthorize("isAuthenticated()")
public class RevisionController {

    private final NeoBridge neoBridge;
    private final MongoBridge mongoBridge;
    private final BranchController branchController;

    @Autowired
    public RevisionController(NeoBridge neoBridge,
                              MongoBridge mongoBridge,
                              BranchController branchController) {
        this.neoBridge = neoBridge;
        this.mongoBridge = mongoBridge;
        this.branchController = branchController;
        assert null != neoBridge;
        assert null != mongoBridge;
        assert null != branchController;
    }

    /**
     * Data transferring object for commit
     */
    private class CommitDto {
        private Commit commit;
        private ArrayList<Blob> blobs;

        CommitDto() {}

        public Commit getCommit() {
            return commit;
        }

        public void setCommit(Commit commit) {
            this.commit = commit;
        }

        public List<Blob> getBlobs() {
            return blobs;
        }

        public void setBlobs(ArrayList<Blob> blobs) {
            this.blobs = blobs;
        }
    }

    /**
     * Retrieves commit from branch in repository
     *
     * @param repoId repository id (must be accessible for user)
     * @param branchId branch id in repository
     * @param revision revision number
     * @return commit with blobs (CommitDto), 403 or 404
     */
    @GetMapping("/{repoId}/{branchId}/{revision}/")
    ResponseEntity<?> get(@PathVariable Long repoId,
                          @PathVariable Long branchId,
                          @PathVariable Long revision) {
        if (! mongoBridge.getRepoById(repoId).getBranchIds()
                .contains(branchId)) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
        if (! branchController.hasAccessTo(branchId)) {
            return new ResponseEntity(HttpStatus.FORBIDDEN);
        }
        CommitDto rc = new CommitDto();
        rc.setCommit(neoBridge.getCommitFromBranch(branchId, revision));
        rc.setBlobs(new ArrayList<>());
        rc.getBlobs().addAll(mongoBridge.getBlobsByIds(rc.getBlobs()));
        return ResponseEntity.ok(rc);
    }

    /**
     * Saves commit into repository
     *
     * Example:
     * PUT request to example.com/r/1/1/
     * Request body:
     * {
     *   "commit":{
     *     "message":"Add javadoc",
     *     "timestamp":12345,
     *     "parent_ids":[1, 4, 3]
     *   },
     *   "blobs":[
     *     {
     *       "name":"CMakeLists.txt",
     *       "mode":13,
     *       "content":"base64encodedcontent"
     *     },
     *     {
     *       "name":"src/main.cxx",
     *       "mode":15,
     *       "content":"base64encodedcontent"
     *     }
     *   ]
     * }
     *
     *
     * @param repoId (see get())
     * @param branchId (see get())
     * @param commitDto (see get())
     * @return (see get())
     */
    @PutMapping("/{repoId}/{branchId}/")
    ResponseEntity<?> add(@PathVariable Long repoId,
                          @PathVariable Long branchId,
                          @RequestBody CommitDto commitDto) {
        if (! mongoBridge.getRepoById(repoId).getBranchIds()
                .contains(branchId)) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
        if (! branchController.hasAccessTo(branchId)) {
            return new ResponseEntity(HttpStatus.FORBIDDEN);
        }
        if (null == commitDto.commit || null == commitDto.blobs) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        Commit commit = commitDto.getCommit();
        if (! commit.getParentIds().contains(neoBridge.getBranchHead(branchId))) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        for (Long r : commit.getParentIds()) {
            // TODO: check parents in repo
        }
        commit.setRevision(null);
        commit.setAuthorId(mongoBridge.getUserByUsername(SecurityContextHolder
                .getContext().getAuthentication().getName()).getId());
        ArrayList<Blob> blobs = commitDto.blobs;
        commit.setBlobIds(null);
        for (int i = 0; i < blobs.size(); ++i) {
            commit.getBlobIds().add(mongoBridge.addBlob(blobs.get(i)).getId());
        }
        // TODO: move branch upper
        return ResponseEntity.ok(neoBridge.saveCommit(commit));
    }

}
