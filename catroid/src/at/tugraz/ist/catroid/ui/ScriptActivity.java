/**
 *  Catroid: An on-device graphical programming language for Android devices
 *  Copyright (C) 2010  Catroid development team
 *  (<http://code.google.com/p/catroid/wiki/Credits>)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.tugraz.ist.catroid.ui;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import at.tugraz.ist.catroid.Consts;
import at.tugraz.ist.catroid.R;
import at.tugraz.ist.catroid.constructionSite.content.ProjectManager;
import at.tugraz.ist.catroid.content.Script;
import at.tugraz.ist.catroid.content.Sprite;
import at.tugraz.ist.catroid.content.bricks.SetCostumeBrick;
import at.tugraz.ist.catroid.io.StorageHandler;
import at.tugraz.ist.catroid.stage.StageActivity;
import at.tugraz.ist.catroid.ui.adapter.BrickAdapter;
import at.tugraz.ist.catroid.ui.dialogs.AddBrickDialog;
import at.tugraz.ist.catroid.ui.dragndrop.DragNDropListView;
import at.tugraz.ist.catroid.utils.Utils;

public class ScriptActivity extends Activity implements OnDismissListener, OnCancelListener {
	private BrickAdapter adapter;
	private DragNDropListView listView;
	private Sprite sprite;
	private Script scriptToEdit;
	private final static int DELETE = 0;

	private void initListeners() {
		sprite = ProjectManager.getInstance().getCurrentSprite();
		listView = (DragNDropListView) findViewById(R.id.brickListView);
		adapter = new BrickAdapter(this, ProjectManager.getInstance().getCurrentSprite(), listView);
		if (adapter.getGroupCount() > 0) {
			ProjectManager.getInstance().setCurrentScript(adapter.getGroup(adapter.getGroupCount() - 1));
		}

		listView.setTrashView((ImageView) findViewById(R.id.trash));
		listView.setOnCreateContextMenuListener(this);
		listView.setOnDropListener(adapter);
		listView.setOnRemoveListener(adapter);
		listView.setAdapter(adapter);
		//listView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		listView.setGroupIndicator(null);
		listView.setOnGroupClickListener(adapter);
		registerForContextMenu(listView);

		Button mainMenuButton = (Button) findViewById(R.id.mainMenuButton);
		mainMenuButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(ScriptActivity.this, MainMenuActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
			}
		});

		Button toStageButton = (Button) findViewById(R.id.toStageButton);
		toStageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(ScriptActivity.this, StageActivity.class);
				startActivity(intent);
			}
		});

		Button addBrickButton = (Button) findViewById(R.id.addBrickButton);
		addBrickButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showDialog(Consts.DIALOG_ADD_BRICK);
			}
		});
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_script);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;

		switch (id) {
			case Consts.DIALOG_ADD_BRICK:
				dialog = new AddBrickDialog(this);
				dialog.setOnDismissListener(this);
				break;
			default:
				dialog = null;
				break;
		}
		return dialog;
	}

	@Override
	protected void onPause() {
		super.onPause();
		ProjectManager projectManager = ProjectManager.getInstance();
		if (projectManager.getCurrentProject() != null) {
			projectManager.saveProject(this);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		initListeners();
		if (adapter.getGroupCount() > 0) {
			listView.expandGroup(adapter.getGroupCount() - 1);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!Utils.checkForSdCard(this)) {
			return;
		}
	}

	public void onDismiss(DialogInterface dialog) {
		for (int i = 0; i < adapter.getGroupCount() - 1; ++i) {
			listView.collapseGroup(i);
		}

		adapter.notifyDataSetChanged();
		if (adapter.getGroupCount() > 0) {
			listView.expandGroup(adapter.getGroupCount() - 1);
		}
	}

	public void onCancel(DialogInterface arg0) {
		adapter.notifyDataSetChanged();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		/*
		 * This is used for getting the results of the gallery intent when the
		 * user selected an image. requestCode holds the ID / position of the
		 * brick that issued the request. If and when we have different kinds of
		 * intents we need to find a better way.
		 */

		if (resultCode == RESULT_OK) {
			SetCostumeBrick affectedBrick = (SetCostumeBrick) adapter
					.getChild(adapter.getGroupCount() - 1, requestCode);
			if (affectedBrick != null) {
				Uri selectedImageUri = data.getData();
				String selectedImagePath = getPathFromContentUri(selectedImageUri);
				try {
					if (affectedBrick.getImagePath() != null) {
						StorageHandler.getInstance().deleteFile(affectedBrick.getImagePath());
					}
					File outputFile = StorageHandler.getInstance().copyImage(
							ProjectManager.getInstance().getCurrentProject().getName(), selectedImagePath);
					if (outputFile != null) {
						affectedBrick.setCostume(outputFile.getName());
						adapter.notifyDataSetChanged();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public String getPathFromContentUri(Uri uri) {
		String[] projection = { MediaStore.Images.Media.DATA };
		Cursor cursor = managedQuery(uri, projection, null, null, null);
		int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
		cursor.moveToFirst();
		return cursor.getString(columnIndex);
	}

	public BrickAdapter getAdapter() {
		return adapter;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		if (view.getId() == R.id.brickListView) {
			ExpandableListView.ExpandableListContextMenuInfo info =
					(ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
			menu.setHeaderTitle("Script Menu");

			if (ExpandableListView.getPackedPositionType(info.packedPosition) == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
				return;
			}

			int position = ExpandableListView.getPackedPositionGroup(info.packedPosition);
			scriptToEdit = adapter.getGroup(position);

			menu.add(Menu.NONE, 0, 0, this.getString(R.string.delete_script_button));
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case DELETE:
				sprite.getScriptList().remove(scriptToEdit);
				if (sprite.getScriptList().isEmpty()) {
					ProjectManager.getInstance().setCurrentScript(null);
					adapter.notifyDataSetChanged();
					return false;
				}
				int lastScriptIndex = sprite.getScriptList().size() - 1;
				Script lastScript = sprite.getScriptList().get(lastScriptIndex);
				ProjectManager.getInstance().setCurrentScript(lastScript);
				adapter.notifyDataSetChanged();
				listView.expandGroup(adapter.getGroupCount() - 1);
		}
		return true;
	}
}
