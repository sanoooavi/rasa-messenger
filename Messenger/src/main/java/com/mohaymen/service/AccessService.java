package com.mohaymen.service;

import com.mohaymen.model.entity.Account;
import com.mohaymen.model.entity.Profile;
import com.mohaymen.model.json_item.LoginInfo;
import com.mohaymen.model.supplies.ChatType;
import com.mohaymen.model.supplies.Status;
import com.mohaymen.repository.AccountRepository;
import com.mohaymen.repository.ProfileRepository;
import com.mohaymen.security.JwtHandler;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import java.awt.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import com.mohaymen.security.SaltGenerator;

@Service
public class AccessService {

    private final AccountRepository accountRepository;

    private final ProfileRepository profileRepository;

    private final SearchService searchService;

    public AccessService(AccountRepository accountRepository, ProfileRepository profileRepository, SearchService searchService) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.searchService = searchService;
    }

    public LoginInfo login(String email, byte[] password) throws Exception {
        Optional<Account> account = accountRepository.findByEmail(email);
        if (account.isEmpty())
            throw new Exception("User not found");

        byte[] checkPassword = getHashed(combineArray(password, account.get().getSalt()));

        if (!Arrays.equals(checkPassword, account.get().getPassword()))
            throw new Exception("Wrong password");

        return LoginInfo.builder()
                .message("success")
                .jwt(JwtHandler.generateAccessToken(account.get().getId()))
                .profile(account.get().getProfile())
                .build();
    }

    private Profile profileExists(String username) {
        Optional<Profile> profile = profileRepository.findByHandle(username);
        return profile.orElse(null);
    }

    private Account emailExists(String email) {
        Optional<Account> account = accountRepository.findByEmail(email);
        return account.orElse(null);
    }

    public Boolean infoValidation(String email) {
        Account account = emailExists(email);
        if(account == null)
            return true;
        return false;
    }

    public LoginInfo signup(String name, String email, byte[] password) throws Exception {
        if(!infoValidation(email))
            throw new Exception("information is not valid");

        Profile profile = new Profile();
        profile.setHandle(email);
        profile.setProfileName(name);
        profile.setType(ChatType.USER);
        profile.setDefaultProfileColor(generateColor(email));
        profileRepository.save(profile);

        byte[] salt = SaltGenerator.getSaltArray();

        Account account = new Account();
        account.setProfile(profile);
        account.setEmail(email);
        account.setLastSeen(LocalDateTime.now());
        account.setPassword(configPassword(password, salt));
        account.setStatus(Status.DEFAULT);
        account.setSalt(salt);
        accountRepository.save(account);

        // add user to search index

        searchService.addUser(account);

        return LoginInfo.builder()
                .message("success")
                .jwt(JwtHandler.generateAccessToken(account.getId()))
                .profile(account.getProfile())
                .build();
    }

    public Profile deleteProfile(Profile profile){
        UUID uuid = UUID.randomUUID();
        profile.setHandle(profile.getHandle() + uuid);
        profile.setDeleted(true);
        profileRepository.save(profile);
        return profile;
    }

    public void deleteAccount(Long id, byte[] password) throws Exception {
        Profile profile = deleteProfile(profileRepository.findById(id).get());
        Account account = accountRepository.findByProfile(profile).get();

        byte[] checkPassword = getHashed(combineArray(password, account.getSalt()));

        if (!Arrays.equals(checkPassword, account.getPassword()))
            throw new Exception("Wrong password");

        accountRepository.delete(account);
    }

    public static String generateColor(String inputString) {
        int seed = inputString.hashCode();
        Random random = new Random(seed);
        int hue = random.nextInt(360);
        Color color = Color.getHSBColor(hue / 360f,0.5f, 0.9f);
        return String.format("#%06x", color.getRGB() & 0x00FFFFFF);
    }

    public byte[] configPassword(byte[] password, byte[] saltArray) {
        byte[] combined = combineArray(password, saltArray);
        return getHashed(combined);
    }

    public byte[] combineArray(byte[] arr1, byte[] arr2) {
        byte[] combined = new byte[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, combined, 0, arr1.length);
        System.arraycopy(arr2, 0, combined, arr1.length, arr2.length);
        return combined;
    }

    @SneakyThrows
    public byte[] getHashed(byte[] bytes) {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        return messageDigest.digest(bytes);
    }

}