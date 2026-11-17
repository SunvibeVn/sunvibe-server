package vn.sunvibe.brokerage.core.application.command;

import java.util.List;

/**
 * The owner confirms one or several sent versions with one password (brokerage-agreement US-2 AC-2, AC-4; E05-S5 spec
 * S5-D5): the items in the order given, no agreement twice (the converter checks it).
 *
 * @param currentPassword  the account's current password; never logged
 * @param statementVersion the statement version the owner read
 */
public record AgreementConfirmCommand(String currentPassword, String statementVersion,
                                      List<AgreementVersionInput> versions) {

    public AgreementConfirmCommand {
        versions = List.copyOf(versions);
    }

    /** No password, and no statement version, in any log line. */
    @Override
    public String toString() {
        return "AgreementConfirmCommand[" + versions.size() + " versions]";
    }
}
