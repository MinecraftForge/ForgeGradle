/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.util.json;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;

public class DateAdapter implements JsonDeserializer<Date>, JsonSerializer<Date>
{
    private final DateFormat enUsFormat = DateFormat.getDateTimeInstance(2, 2, Locale.US);
    private final DateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    @Override
    public JsonElement serialize(Date value, Type type, JsonSerializationContext context)
    {
        synchronized(enUsFormat)
        {
            String ret = this.iso8601Format.format(value);
            return new JsonPrimitive(ret.substring(0, 22) + ":" + ret.substring(22));
        }
    }

    @Override
    public Date deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException
    {
        if (!(json instanceof JsonPrimitive))
        {
            throw new JsonParseException("Date was not string: " + json);
        }
        if (type != Date.class)
        {
            throw new IllegalArgumentException(getClass() + " cannot deserialize to " + type);
        }
        String value = json.getAsString();
        synchronized(enUsFormat)
        {
            try
            {
                return enUsFormat.parse(value);
            }
            catch (ParseException e)
            {
                try 
                {
                    return iso8601Format.parse(value);
                }
                catch (ParseException e2)
                {
                    try
                    {
                        String tmp = value.replace("Z", "+00:00");
                        if (tmp.length() < 22)
                            return new Date();
                        else
                            return iso8601Format.parse(tmp.substring(0, 22) + tmp.substring(23));
                    }
                    catch (ParseException e3)
                    {
                        throw new JsonSyntaxException("Invalid date: " + value, e3);
                    }
                }
            }
        }
    }
}
