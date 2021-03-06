package com.nowcoder.service;

import com.nowcoder.dao.LoginTicketDao;
import com.nowcoder.dao.UserDao;
import com.nowcoder.model.HostHolder;
import com.nowcoder.model.LoginTicket;
import com.nowcoder.model.User;
import com.nowcoder.util.JedisAdapter;
import com.nowcoder.util.MailSender;
import com.nowcoder.util.WendaUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import sun.security.krb5.internal.Ticket;

import java.util.*;

@Service
public class UserService {
    @Autowired
    UserDao userDao;
    @Autowired
    LoginTicketDao loginTicketDao;
    @Autowired
    HostHolder hostHolder;
    @Autowired
    JedisAdapter jedisAdapter;
    @Autowired
    MailSender mailSender;

    public User getUser(int id) {
        return userDao.selectById(id);
    }

    public Map<String, String> reg(String username, String password) {
        Map<String, String> map = new HashMap<>();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            map.put("msg", "用户名或密码不能为空");
            return map;
        }

        User user = userDao.selectByName(username);
        if (user != null) {
            map.put("msg", "用户名已存在");
            return map;
        }

        String salt = UUID.randomUUID().toString().substring(0, 5);
        jedisAdapter.hset(salt, username, password);
        Map<String, Object> mapHtml = new HashMap<>();
        mapHtml.put("salt", salt);
        mapHtml.put("username", username);
        mailSender.sendWithHTMLTemplate(username, "邮箱注册验证", "mails/regmail_check.html", mapHtml);

        map.put("msg","请前往邮箱进行注册");
        return map;

    }

    public Map<String, String> regok(String salt, String username) {
        String password = jedisAdapter.hget(salt, username);
        if (password == null) {
            return null;
        }
        User user;
        // 密码强度
        user = new User();
        user.setName(username);
        user.setSalt(UUID.randomUUID().toString().substring(0, 5));

        String head = String.format("http://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000));
        user.setHeadUrl(head);
        user.setPassword(WendaUtil.MD5(password + user.getSalt()));
        userDao.addUser(user);

        // 登陆
        String ticket = addLoginTicket(user.getId());
        Map<String, String> map = new HashMap<>();
        map.put("ticket", ticket);
        return map;
    }


    public Map<String, String> login(String username, String password) {
        Map<String, String> map = new HashMap<>();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            map.put("msg", "用户名或密码不能为空");
            return map;
        }

        User user = userDao.selectByName(username);
        if (user == null) {
            map.put("msp", "用户不存在");
            return map;
        }

        String pass = WendaUtil.MD5(password + user.getSalt());
        if (!user.getPassword().equals(pass)) {
            map.put("msg", "密码不正确");
            return map;
        }

        map.put("ticket", addLoginTicket(user.getId()));
        return map;

    }

    public String addLoginTicket(int userId) {
        LoginTicket ticket = new LoginTicket();
        ticket.setUserId(userId);
        Date date = new Date();
        date.setTime(date.getTime() + 1000 * 3600 * 24);
        ticket.setExpired(date);
        ticket.setStatus(0);
        ticket.setTicket(UUID.randomUUID().toString().replaceAll("-", ""));
        loginTicketDao.addTicket(ticket);
        return ticket.getTicket();
    }

    public void logout() {
        User user = hostHolder.getUser();
        loginTicketDao.updateTicket(user.getId());
        hostHolder.clear();
    }

    public User getUserByName(String name) {
        return userDao.selectByName(name);
    }

}
