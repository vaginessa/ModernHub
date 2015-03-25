/*
 * Copyright 2012 GitHub Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pw.bastiaan.github.ui.repo;

import static pw.bastiaan.github.Intents.EXTRA_USER;
import static pw.bastiaan.github.ResultCodes.RESOURCE_CHANGED;
import static java.util.Locale.US;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ListView;

import com.github.kevinsawicki.wishlist.SingleTypeAdapter;
import com.github.kevinsawicki.wishlist.ViewFinder;

import pw.bastiaan.github.ThrowableLoader;
import pw.bastiaan.github.persistence.AccountDataManager;
import pw.bastiaan.github.ui.HeaderFooterListAdapter;
import pw.bastiaan.github.ui.ItemListFragment;
import pw.bastiaan.github.ui.LightAlertDialog;
import pw.bastiaan.github.ui.user.OrganizationSelectionListener;
import pw.bastiaan.github.ui.user.OrganizationSelectionProvider;
import pw.bastiaan.github.ui.user.UserViewActivity;
import pw.bastiaan.github.util.AvatarLoader;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.User;

import pw.bastiaan.github.RequestCodes;

/**
 * Fragment to display a list of {@link Repository} instances
 */
public class RepositoryListFragment extends ItemListFragment<Repository>
    implements OrganizationSelectionListener {

    @Inject
    private AccountDataManager cache;

    @Inject
    private AvatarLoader avatars;

    private final AtomicReference<User> org = new AtomicReference<>();

    private RecentRepositories recentRepos;

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        User org = this.org.get();
        if (org != null)
            outState.putSerializable(EXTRA_USER, org);
    }

    @Override
    protected void configureList(Activity activity, ListView listView) {
        super.configureList(activity, listView);

        listView.setDividerHeight(0);
        updateHeaders(items);
    }

    @Override
    public void onDetach() {
        if (getActivity() != null && getActivity() instanceof OrganizationSelectionProvider) {
            OrganizationSelectionProvider selectionProvider = (OrganizationSelectionProvider) getActivity();
            selectionProvider.removeListener(this);
        }

        super.onDetach();
    }

    @Override
    public void onOrganizationSelected(final User organization) {
        User previousOrg = org.get();
        int previousOrgId = previousOrg != null ? previousOrg.getId() : -1;
        org.set(organization);

        if (recentRepos != null)
            recentRepos.saveAsync();

        // Only hard refresh if view already created and org is changing
        if (previousOrgId != organization.getId()) {
            Activity activity = getActivity();
            if (activity != null)
                recentRepos = new RecentRepositories(activity, organization);

            refreshWithProgress();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Activity activity = getActivity();
        User currentOrg = null;

        if (getActivity() instanceof OrganizationSelectionProvider)
            currentOrg = ((OrganizationSelectionProvider) activity)
                .addListener(this);

        if (getArguments() != null && getArguments().containsKey("org"))
            currentOrg = (User) getArguments().getSerializable("org");

        if (currentOrg == null && savedInstanceState != null)
            currentOrg = (User) savedInstanceState.getSerializable(EXTRA_USER);
        org.set(currentOrg);
        if (currentOrg != null)
            recentRepos = new RecentRepositories(activity, currentOrg);

        setEmptyText(pw.bastiaan.github.R.string.no_repositories);

        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Refresh if the viewed repository was (un)starred
        if (requestCode == RequestCodes.REPOSITORY_VIEW && resultCode == RESOURCE_CHANGED) {
            forceRefresh();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onListItemClick(ListView list, View v, int position, long id) {
        Repository repo = (Repository) list.getItemAtPosition(position);
        if (recentRepos != null)
            recentRepos.add(repo);

        startActivityForResult(RepositoryViewActivity.createIntent(repo),
            RequestCodes.REPOSITORY_VIEW);
    }

    @Override
    public boolean onListItemLongClick(ListView list, View v, int position,
        long itemId) {
        if (!isUsable())
            return false;

        final Repository repo = (Repository) list.getItemAtPosition(position);
        if (repo == null)
            return false;

        final AlertDialog dialog = LightAlertDialog.create(getActivity());
        dialog.setCanceledOnTouchOutside(true);

        dialog.setTitle(repo.generateId());

        View view = getActivity().getLayoutInflater().inflate(
            pw.bastiaan.github.R.layout.repo_dialog, null);
        ViewFinder finder = new ViewFinder(view);

        final User owner = repo.getOwner();
        avatars.bind(finder.imageView(pw.bastiaan.github.R.id.iv_owner_avatar), owner);
        finder.setText(pw.bastiaan.github.R.id.tv_owner_name, getString(pw.bastiaan.github.R.string.navigate_to_user, owner.getLogin()));
        finder.onClick(pw.bastiaan.github.R.id.ll_owner_area, new OnClickListener() {

            public void onClick(View v) {
                dialog.dismiss();

                viewUser(owner);
            }
        });

        if ((recentRepos != null) && (recentRepos.contains(repo))) {
            finder.find(pw.bastiaan.github.R.id.divider).setVisibility(View.VISIBLE);
            finder.find(pw.bastiaan.github.R.id.ll_recent_repo_area).setVisibility(View.VISIBLE);
            finder.onClick(pw.bastiaan.github.R.id.ll_recent_repo_area, new OnClickListener() {

                public void onClick(View v) {
                    dialog.dismiss();

                    recentRepos.remove(repo);
                    refresh();
                }
            });
        }

        dialog.setView(view);
        dialog.show();

        return true;
    }

    private void viewUser(User user) {
        if (org.get().getId() != user.getId())
            startActivity(UserViewActivity.createIntent(user));
    }

    @Override
    public void onStop() {
        super.onStop();

        if (recentRepos != null)
            recentRepos.saveAsync();
    }

    private void updateHeaders(final List<Repository> repos) {
        HeaderFooterListAdapter<?> rootAdapter = getListAdapter();
        if (rootAdapter == null)
            return;

        DefaultRepositoryListAdapter adapter = (DefaultRepositoryListAdapter) rootAdapter
            .getWrappedAdapter();
        adapter.clearHeaders();

        if (repos.isEmpty())
            return;

        // Add recent header if at least one recent repository
        Repository first = repos.get(0);
        if (recentRepos.contains(first))
            adapter.registerHeader(first, getString(pw.bastiaan.github.R.string.recently_viewed));

        // Advance past all recent repositories
        int index;
        Repository current = null;
        for (index = 0; index < repos.size(); index++) {
            Repository repository = repos.get(index);
            if (recentRepos.contains(repository.getId()))
                current = repository;
            else
                break;
        }

        if (index >= repos.size())
            return;

        if (current != null)
            adapter.registerNoSeparator(current);

        // Register header for first character
        current = repos.get(index);
        char start = Character.toLowerCase(current.getName().charAt(0));
        adapter.registerHeader(current,
            Character.toString(start).toUpperCase(US));

        char previousHeader = start;
        for (index = index + 1; index < repos.size(); index++) {
            current = repos.get(index);
            char repoStart = Character.toLowerCase(current.getName().charAt(0));
            if (repoStart <= start)
                continue;

            // Don't include separator for the last element of the previous
            // character
            if (previousHeader != repoStart)
                adapter.registerNoSeparator(repos.get(index - 1));

            adapter.registerHeader(current, Character.toString(repoStart)
                .toUpperCase(US));
            previousHeader = repoStart;
            start = repoStart++;
        }

        // Don't include separator for last element
        adapter.registerNoSeparator(repos.get(repos.size() - 1));
    }

    @Override
    public Loader<List<Repository>> onCreateLoader(int id, final Bundle args) {
        return new ThrowableLoader<List<Repository>>(getActivity(), items) {

            @Override
            public List<Repository> loadData() throws Exception {
                User org = RepositoryListFragment.this.org.get();
                if (org == null)
                    return Collections.emptyList();

                List<Repository> repos = cache.getRepos(org,
                    isForceRefresh(args));
                Collections.sort(repos, recentRepos);
                updateHeaders(repos);
                return repos;
            }
        };
    }

    @Override
    protected SingleTypeAdapter<Repository> createAdapter(List<Repository> items) {
        return new DefaultRepositoryListAdapter(getActivity()
            .getLayoutInflater(),
            items.toArray(new Repository[items.size()]), org);
    }

    @Override
    protected int getErrorMessage(Exception exception) {
        return pw.bastiaan.github.R.string.error_repos_load;
    }
}
