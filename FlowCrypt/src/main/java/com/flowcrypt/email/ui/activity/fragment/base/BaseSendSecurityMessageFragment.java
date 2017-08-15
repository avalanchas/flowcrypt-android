/*
 * Business Source License 1.0 © 2017 FlowCrypt Limited (tom@cryptup.org).
 * Use limitations apply. See https://github.com/FlowCrypt/flowcrypt-android/blob/master/LICENSE
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment.base;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.Loader;
import android.text.SpannableStringBuilder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.flowcrypt.email.R;
import com.flowcrypt.email.api.email.model.OutgoingMessageInfo;
import com.flowcrypt.email.js.Js;
import com.flowcrypt.email.js.PgpContact;
import com.flowcrypt.email.model.MessageEncryptionType;
import com.flowcrypt.email.model.UpdateInfoAboutPgpContactsResult;
import com.flowcrypt.email.model.results.LoaderResult;
import com.flowcrypt.email.ui.activity.ImportPublicKeyActivity;
import com.flowcrypt.email.ui.activity.fragment.dialog.NoPgpFoundDialogFragment;
import com.flowcrypt.email.ui.activity.listeners.OnChangeMessageEncryptedTypeListener;
import com.flowcrypt.email.ui.loader.PrepareEncryptedRawMessageAsyncTaskLoader;
import com.flowcrypt.email.ui.loader.UpdateInfoAboutPgpContactsAsyncTaskLoader;
import com.flowcrypt.email.ui.widget.CustomChipSpanChipCreator;
import com.flowcrypt.email.ui.widget.PGPContactChipSpan;
import com.flowcrypt.email.ui.widget.SingleCharacterSpanChipTokenizer;
import com.flowcrypt.email.util.GeneralUtil;
import com.flowcrypt.email.util.UIUtil;
import com.hootsuite.nachos.NachoTextView;
import com.hootsuite.nachos.validator.ChipifyingNachoValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The base fragment for sending an encrypted message;
 *
 * @author DenBond7
 *         Date: 10.05.2017
 *         Time: 11:27
 *         E-mail: DenBond7@gmail.com
 */

public abstract class BaseSendSecurityMessageFragment extends BaseGmailFragment {
    protected static final int REQUEST_CODE_NO_PGP_FOUND_DIALOG = 100;
    private static final int REQUEST_CODE_IMPORT_PUBLIC_KEY = 101;

    protected Js js;
    protected OnMessageSendListener onMessageSendListener;
    protected OnChangeMessageEncryptedTypeListener onChangeMessageEncryptedTypeListener;
    protected boolean isUpdateInfoAboutContactsEnable = true;
    protected boolean isUpdatedInfoAboutContactCompleted = true;
    protected boolean isMessageSendingNow;
    protected List<PgpContact> pgpContacts;
    protected NachoTextView editTextRecipients;

    public BaseSendSecurityMessageFragment() {
        pgpContacts = new ArrayList<>();
    }

    public abstract void onMessageEncryptionTypeChange(MessageEncryptionType messageEncryptionType);

    /**
     * Generate an outgoing message info from entered information by user.
     *
     * @return <tt>OutgoingMessageInfo</tt> Return a created OutgoingMessageInfo object which
     * contains information about an outgoing message.
     */
    public abstract OutgoingMessageInfo getOutgoingMessageInfo();

    /**
     * Get an update information about contacts progress view.
     *
     * @return {@link View}
     */
    public abstract View getUpdateInfoAboutContactsProgressBar();

    /**
     * Get a list of emails, that will be checked to find an information about public keys.
     *
     * @return A list of emails.
     */
    public abstract List<String> getContactsEmails();

    /**
     * Do a lot of checks to validate an outgoing message info.
     *
     * @return <tt>Boolean</tt> true if all information is correct, false otherwise.
     */
    public abstract boolean isAllInformationCorrect();

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnMessageSendListener) {
            this.onMessageSendListener = (OnMessageSendListener) context;
        } else throw new IllegalArgumentException(context.toString() + " must implement " +
                OnMessageSendListener.class.getSimpleName());

        if (context instanceof OnChangeMessageEncryptedTypeListener) {
            this.onChangeMessageEncryptedTypeListener = (OnChangeMessageEncryptedTypeListener)
                    context;
        } else throw new IllegalArgumentException(context.toString() + " must implement " +
                OnChangeMessageEncryptedTypeListener.class.getSimpleName());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        try {
            js = new Js(getContext(), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initChipsView(view);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_NO_PGP_FOUND_DIALOG:
                switch (resultCode) {
                    case NoPgpFoundDialogFragment.RESULT_CODE_SWITCH_TO_STANDARD_EMAIL:
                        switchMessageEncryptionType(MessageEncryptionType.STANDARD);
                        break;

                    case NoPgpFoundDialogFragment.RESULT_CODE_IMPORT_THEIR_PUBLIC_KEY:
                        if (data != null) {
                            PgpContact pgpContact = data.getParcelableExtra(NoPgpFoundDialogFragment
                                    .EXTRA_KEY_PGP_CONTACT);

                            if (pgpContact != null) {
                                startActivityForResult(
                                        ImportPublicKeyActivity.newIntent(getContext(),
                                                getString(R.string.import_public_key), pgpContact),
                                        REQUEST_CODE_IMPORT_PUBLIC_KEY);
                            }
                        }

                        break;
                }
                break;

            case REQUEST_CODE_IMPORT_PUBLIC_KEY:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Toast.makeText(getContext(), R.string.key_successfully_imported,
                                Toast.LENGTH_SHORT).show();

                        getLoaderManager().restartLoader(
                                R.id.loader_id_update_info_about_pgp_contacts, null,
                                BaseSendSecurityMessageFragment.this);
                        break;
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.setGroupVisible(0, !isMessageSendingNow);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuActionSend:
                if (getSnackBar() != null) {
                    getSnackBar().dismiss();
                }

                if (isUpdatedInfoAboutContactCompleted) {
                    UIUtil.hideSoftInput(getContext(), getView());
                    if (GeneralUtil.isInternetConnectionAvailable(getContext())) {
                        if (isAllInformationCorrect()) {
                            getLoaderManager().restartLoader(
                                    R.id.loader_id_prepare_encrypted_message, null, this);
                        }
                    } else {
                        UIUtil.showInfoSnackbar(getView(), getString(R.string
                                .internet_connection_is_not_available));
                    }
                } else {
                    Toast.makeText(getContext(), R.string
                                    .please_wait_while_information_about_contacts_will_be_updated,
                            Toast.LENGTH_SHORT).show();
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Loader<LoaderResult> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case R.id.loader_id_prepare_encrypted_message:
                isUpdateInfoAboutContactsEnable = false;
                isMessageSendingNow = true;
                getActivity().invalidateOptionsMenu();
                statusView.setVisibility(View.GONE);
                UIUtil.exchangeViewVisibility(getContext(), true, progressView, getContentView());
                OutgoingMessageInfo outgoingMessageInfo = getOutgoingMessageInfo();
                return new PrepareEncryptedRawMessageAsyncTaskLoader(getContext(),
                        outgoingMessageInfo, onChangeMessageEncryptedTypeListener
                        .getMessageEncryptionType());

            case R.id.loader_id_update_info_about_pgp_contacts:
                pgpContacts.clear();
                getUpdateInfoAboutContactsProgressBar().setVisibility(View.VISIBLE);
                isUpdatedInfoAboutContactCompleted = false;
                return new UpdateInfoAboutPgpContactsAsyncTaskLoader(getContext(),
                        getContactsEmails());

            default:
                return null;
        }
    }

    @Override
    public void handleSuccessLoaderResult(int loaderId, Object result) {
        switch (loaderId) {
            case R.id.loader_id_prepare_encrypted_message:
                isUpdateInfoAboutContactsEnable = true;
                if (result != null) {
                    sendEncryptMessage((String) result);
                } else {
                    notifyUserAboutErrorWhenSendMessage();
                }
                break;

            case R.id.loader_id_update_info_about_pgp_contacts:
                UpdateInfoAboutPgpContactsResult updateInfoAboutPgpContactsResult
                        = (UpdateInfoAboutPgpContactsResult) result;

                isUpdatedInfoAboutContactCompleted = true;
                getUpdateInfoAboutContactsProgressBar().setVisibility(View.INVISIBLE);

                if (updateInfoAboutPgpContactsResult != null
                        && updateInfoAboutPgpContactsResult.getUpdatedPgpContacts() != null) {
                    pgpContacts = updateInfoAboutPgpContactsResult.getUpdatedPgpContacts();
                }

                if (updateInfoAboutPgpContactsResult == null
                        || !updateInfoAboutPgpContactsResult.isAllInfoReceived()) {
                    Toast.makeText(getContext(),
                            R.string.info_about_some_contacts_not_received,
                            Toast.LENGTH_SHORT).show();
                }

                if (!pgpContacts.isEmpty()) {
                    updateChips();
                }
                break;

            default:
                super.handleSuccessLoaderResult(loaderId, result);
        }
    }

    @Override
    public void handleFailureLoaderResult(int loaderId, Exception e) {
        super.handleFailureLoaderResult(loaderId, e);
        switch (loaderId) {
            case R.id.loader_id_update_info_about_pgp_contacts:
                isUpdatedInfoAboutContactCompleted = true;
                getUpdateInfoAboutContactsProgressBar().setVisibility(View.INVISIBLE);
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<LoaderResult> loader) {
        super.onLoaderReset(loader);
        switch (loader.getId()) {
            case R.id.loader_id_prepare_encrypted_message:
                isUpdateInfoAboutContactsEnable = true;
                break;
        }
    }

    @Override
    public void onErrorOccurred(int requestCode, int errorType, Exception e) {
        notifyUserAboutErrorWhenSendMessage();
    }

    /**
     * Notify the user about an error which occurred when we send a message.
     */
    public void notifyUserAboutErrorWhenSendMessage() {
        isMessageSendingNow = false;
        getActivity().invalidateOptionsMenu();
        UIUtil.exchangeViewVisibility(getContext(), false, progressView, getContentView());
        showInfoSnackbar(getView(), getString(R.string.error_occurred_while_sending_message));
    }

    /**
     * Check the message sending status
     *
     * @return true if message was sent, false otherwise.
     */
    public boolean isMessageSendingNow() {
        return isMessageSendingNow;
    }

    /**
     * Switch the message encryption type.
     *
     * @param messageEncryptionType The new message encryption type.
     */
    protected void switchMessageEncryptionType(MessageEncryptionType messageEncryptionType) {
        onChangeMessageEncryptedTypeListener.onMessageEncryptionTypeChange(messageEncryptionType);
    }

    /**
     * Check that all recipients have PGP.
     *
     * @return true if all recipients have PGP, other wise false.
     */
    protected boolean isAllRecipientsHavePGP(boolean isShowRemoveAction) {
        for (PgpContact pgpContact : pgpContacts) {
            if (!pgpContact.getHasPgp()) {
                showNoPgpFoundDialog(pgpContact, isShowRemoveAction);
                return false;
            }
        }

        return true;
    }

    /**
     * This method does update chips in the recipients field.
     */
    protected void updateChips() {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder
                (editTextRecipients.getText());

        PGPContactChipSpan[] pgpContactChipSpans = spannableStringBuilder.getSpans(0,
                editTextRecipients.length(), PGPContactChipSpan.class);

        if (pgpContactChipSpans.length > 0) {
            for (PgpContact pgpContact : pgpContacts) {
                for (PGPContactChipSpan pgpContactChipSpan : pgpContactChipSpans) {
                    if (pgpContact.getEmail().equalsIgnoreCase(pgpContactChipSpan.getText()
                            .toString())) {
                        pgpContactChipSpan.setHasPgp(pgpContact.getHasPgp());
                        break;
                    }
                }
            }
            editTextRecipients.invalidateChips();
        }
    }

    protected void initChipsView(View view) {
        editTextRecipients = (NachoTextView) view.findViewById(R.id.editTextRecipient);
        editTextRecipients.setNachoValidator(new ChipifyingNachoValidator());
        editTextRecipients.setIllegalCharacters(',');
        editTextRecipients.setChipTokenizer(
                new SingleCharacterSpanChipTokenizer(getContext(), new CustomChipSpanChipCreator
                        (getContext()),
                        PGPContactChipSpan.class, SingleCharacterSpanChipTokenizer
                        .CHIP_SEPARATOR_WHITESPACE));
    }

    /**
     * Show a dialog where we can select different actions.
     *
     * @param pgpContact         The {@link PgpContact} which will be used when we select the
     *                           remove action.
     * @param isShowRemoveAction true if we want to show the remove action, false otherwise.
     */
    private void showNoPgpFoundDialog(PgpContact pgpContact, boolean isShowRemoveAction) {
        NoPgpFoundDialogFragment noPgpFoundDialogFragment =
                NoPgpFoundDialogFragment.newInstance(pgpContact, isShowRemoveAction);

        noPgpFoundDialogFragment.setTargetFragment(this, REQUEST_CODE_NO_PGP_FOUND_DIALOG);
        noPgpFoundDialogFragment.show(getFragmentManager(), NoPgpFoundDialogFragment.class
                .getSimpleName());
    }

    /**
     * /**
     * Send an encrypted message. Before sending, we do some checks(is all information valid, is
     * internet connection available);
     */
    private void sendEncryptMessage(String encryptedRawMessage) {
        if (onMessageSendListener != null) {
            isMessageSendingNow = true;
            getActivity().invalidateOptionsMenu();
            UIUtil.exchangeViewVisibility(getContext(), true, progressView, getContentView());
            onMessageSendListener.sendMessage(encryptedRawMessage);
        }
    }

    /**
     * This interface will be used when we send a message.
     */
    public interface OnMessageSendListener {
        void sendMessage(String encryptedRawMessage);

        String getSenderEmail();
    }
}
