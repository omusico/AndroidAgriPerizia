package org.droidplanner.android.fragments.account.editor.tool;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;

import org.droidplanner.android.R;
import org.droidplanner.android.activities.EditorActivity;
import org.droidplanner.android.dialogs.SupportYesNoDialog;
import org.droidplanner.android.fragments.helpers.ApiListenerFragment;
import org.droidplanner.android.proxy.mission.MissionProxy;
import org.droidplanner.android.proxy.mission.item.MissionItemProxy;
import org.droidplanner.android.proxy.mission.item.adapters.AdapterMissionItems;


/**
 * This fragment implements and displays the 'tools' used in the editor window
 * to switch between different type of waypoints creation.
 */
public class EditorToolsFragment extends ApiListenerFragment implements SupportYesNoDialog.Listener {

    /**
     * Used as key to retrieve the last selected tool from the bundle passed on
     * fragment creation.
     */
    private static final String STATE_SELECTED_TOOL = "selected_tool";

    public enum EditorTools {
        MARKER, TRASH, SELECTOR, NONE
    }

    public interface EditorToolListener {
        void editorToolChanged(EditorTools tools);

        void skipMarkerClickEvents(boolean skip);

        void zoomToFitSelected();
    }

    private static final IntentFilter eventFilter = new IntentFilter();

    static {
        eventFilter.addAction(AttributeEvent.MISSION_RECEIVED);
    }

    private final BroadcastReceiver eventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case AttributeEvent.MISSION_RECEIVED:
                    setTool(EditorTools.NONE);
                    break;
            }
        }
    };

    /**
     * The marker tool should be set by default.
     */
    private static final EditorTools DEFAULT_TOOL = EditorTools.MARKER;

    private final EditorToolsImpl[] editorToolsImpls = new EditorToolsImpl[EditorTools.values().length];

    {
        editorToolsImpls[EditorTools.MARKER.ordinal()] = new MarkerToolsImpl(this);
        editorToolsImpls[EditorTools.TRASH.ordinal()] = new TrashToolsImpl(this);
        editorToolsImpls[EditorTools.SELECTOR.ordinal()] = new SelectorToolsImpl(this);
        editorToolsImpls[EditorTools.NONE.ordinal()] = new NoneToolsImpl(this);
    }

    EditorToolListener listener;
    private EditorTools tool = DEFAULT_TOOL;
    private MissionProxy mMissionProxy;

    //Sub action views
    private View editorSubTools;
    private Spinner markerItemsSpinner;

    private View clearSubOptions;
    TextView clearMission;
    TextView clearSelected;

    TextView selectAll;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_editor_tools, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            // Retrieve the tool that was last selected before the fragment was destroyed.
            final String toolName = savedInstanceState.getString(STATE_SELECTED_TOOL, tool.name());
            tool = EditorTools.valueOf(toolName);

            for (EditorToolsImpl toolImpl : editorToolsImpls)
                toolImpl.onRestoreInstanceState(savedInstanceState);
        }

        final Context context = getContext();

        editorSubTools = view.findViewById(R.id.editor_sub_tools);

        final MarkerToolsImpl markerToolImpl = (MarkerToolsImpl) editorToolsImpls[EditorTools.MARKER.ordinal()];
        final AdapterMissionItems markerItemsAdapter = new AdapterMissionItems(context,
                R.layout.spinner_drop_down_mission_item, MarkerToolsImpl.MARKER_ITEMS_TYPE);
        markerItemsSpinner = (Spinner) view.findViewById(R.id.marker_items_spinner);
        markerItemsSpinner.setAdapter(markerItemsAdapter);
        markerItemsSpinner.setSelection(markerItemsAdapter.getPosition(markerToolImpl.getSelected()));
        markerItemsSpinner.setOnItemSelectedListener(markerToolImpl);

        final TrashToolsImpl trashToolImpl = (TrashToolsImpl) editorToolsImpls[EditorTools.TRASH.ordinal()];

        clearSubOptions = view.findViewById(R.id.clear_sub_options);

        clearMission = (TextView) view.findViewById(R.id.clear_mission_button);
        clearMission.setOnClickListener(trashToolImpl);

        clearSelected = (TextView) view.findViewById(R.id.clear_selected_button);
        clearSelected.setOnClickListener(trashToolImpl);

        final SelectorToolsImpl selectorToolImpl = (SelectorToolsImpl) editorToolsImpls[EditorTools.SELECTOR.ordinal()];
        selectAll = (TextView) view.findViewById(R.id.select_all_button);
        selectAll.setOnClickListener(selectorToolImpl);

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof EditorToolListener)) {
            throw new IllegalStateException("Parent activity must be an instance of " + EditorToolListener.class
                    .getName());
        }

        listener = (EditorToolListener) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onApiConnected() {
        mMissionProxy = getMissionProxy();
        setToolAndUpdateView(tool);
        getBroadcastManager().registerReceiver(eventReceiver, eventFilter);

        final ImageButton buttonUndo = (ImageButton) getView().findViewById(R.id.editor_tools_undo);
        buttonUndo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setTool(EditorTools.NONE);
                /*
                if (mMissionProxy.canUndoMission())
                    mMissionProxy.undoMission();
                else {
                    Toast.makeText(getContext(), "No operation left to undo.", Toast.LENGTH_SHORT).show();
                }
                */
                ((EditorActivity)getActivity()).generaPercorso();
            }
        });

        if (mMissionProxy != null) {
            for (EditorToolsImpl toolImpl : editorToolsImpls)
                toolImpl.setMissionProxy(mMissionProxy);
        }
    }

    @Override
    public void onApiDisconnected() {
        getBroadcastManager().unregisterReceiver(eventReceiver);
        mMissionProxy = null;

        for (EditorToolsImpl toolImpl : editorToolsImpls)
            toolImpl.setMissionProxy(null);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        // Store the currently selected tool
        savedInstanceState.putString(STATE_SELECTED_TOOL, tool.name());

        for (EditorToolsImpl toolImpl : editorToolsImpls) {
            toolImpl.onSaveInstanceState(savedInstanceState);
        }
    }

    public EditorToolsImpl getToolImpl() {
        return editorToolsImpls[tool.ordinal()];
    }

    private void hideSubTools() {
        if (editorSubTools != null)
            editorSubTools.setVisibility(View.GONE);

        if (selectAll != null)
            selectAll.setVisibility(View.GONE);

        if (clearSubOptions != null)
            clearSubOptions.setVisibility(View.GONE);

        if (markerItemsSpinner != null)
            markerItemsSpinner.setVisibility(View.GONE);

    }

    public EditorTools getTool() {
        return tool;
    }

    /**
     * Updates the selected tool.
     *
     * @param tool selected tool.
     */
    public void setTool(EditorTools tool) {
        setTool(tool, true);
    }

    /**
     * Updates the selected tool, and optionally notify listeners.
     *
     * @param tool            selected tool.
     * @param notifyListeners true to notify listeners, false otherwise.
     */
    private void setTool(EditorTools tool, boolean notifyListeners) {
        if (mMissionProxy != null && mMissionProxy.getItems().size() > 0
                && tool != EditorTools.TRASH
                && tool != EditorTools.SELECTOR
                && tool != EditorTools.NONE) {

            MissionItemProxy lastMissionItem = mMissionProxy.getItems().get(mMissionProxy.getItems().size() - 1);
            switch (lastMissionItem.getMissionItem().getType()) {
                case LAND:
                case RETURN_TO_LAUNCH:
                    tool = EditorTools.NONE;
                    Toast.makeText(getActivity(), getString(R.string.editor_err_land_rtl_added),
                            Toast.LENGTH_SHORT).show();
                    break;

                default:
                    break;
            }
        }

        this.tool = tool;


        updateSubToolsVisibility();

        if (listener != null && notifyListeners) {
            listener.editorToolChanged(this.tool);
        }
    }

    private void updateSubToolsVisibility() {
        hideSubTools();
    }

    /**
     * Updates the selected tool, and the view to match.
     *
     * @param tool selected tool.
     */
    public void setToolAndUpdateView(EditorTools tool) {
        setTool(tool, false);
    }

    @Override
    public void onDialogYes(String dialogTag) {
        getToolImpl().onDialogYes(dialogTag);
    }

    @Override
    public void onDialogNo(String dialogTag) {
        getToolImpl().onDialogNo(dialogTag);
    }

}
