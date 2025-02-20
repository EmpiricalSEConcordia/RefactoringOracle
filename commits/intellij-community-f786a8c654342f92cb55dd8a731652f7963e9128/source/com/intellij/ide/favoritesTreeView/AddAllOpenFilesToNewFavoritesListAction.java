package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ide.IdeBundle;

/**
 * User: anna
 * Date: Feb 28, 2005
 */
class AddAllOpenFilesToNewFavoritesListAction extends AnAction {
 public AddAllOpenFilesToNewFavoritesListAction() {
   super(IdeBundle.message("action.add.all.open.tabs.to.new.favorites.list"),
         IdeBundle.message("action.add.to.new.favorites.list"), IconLoader.getIcon("/general/addFavoritesList.png"));
 }

 public void actionPerformed(AnActionEvent e) {
   final DataContext dataContext = e.getDataContext();
   final FavoritesTreeViewPanel favoritesTreeViewPanel = AddNewFavoritesListAction.doAddNewFavoritesList((Project)dataContext.getData(DataConstants.PROJECT));
   if (favoritesTreeViewPanel != null){
     new AddAllOpenFilesToFavorites(favoritesTreeViewPanel.getName()).actionPerformed(e);
   }
 }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(!AddAllOpenFilesToFavorites.getFilesToAdd(project).isEmpty());
    }
  }
}
